package cash.p.terminal.entities.transactionrecords.beam

import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BeamTransactionRecordConverterTest {
    private val blockchain = Blockchain(BlockchainType.Beam, "Beam", null)
    private val token = Token(Coin("beam", "Beam", "BEAM"), blockchain, TokenType.Native, 8)
    private val source = source("account-a")
    private val converter = BeamTransactionRecordConverter(source, token)

    @Test
    fun convert_eachDirection_preservesSignedAmountAndSeparateFee() {
        BeamTransactionDirection.entries.forEach { direction ->
            val record = converter.convert(transaction(direction = direction))
            val incoming = direction == BeamTransactionDirection.Incoming
            assertEquals(BigDecimal(if (incoming) "1.23456789" else "-1.23456789"), record.mainValue.value)
            assertEquals(BigDecimal("0.00000100"), record.fee.value)
            assertEquals(direction == BeamTransactionDirection.Self, record.sentToSelf)
            assertEquals(
                if (incoming) TransactionRecordType.BEAM_INCOMING else TransactionRecordType.BEAM_OUTGOING,
                record.transactionRecordType,
            )
            assertSame(token, record.mainValue.token)
            assertSame(token, record.fee.token)
            assertEquals(direction, record.direction)
            assertNull(record.from)
            assertNull(record.to)
            assertNull(record.memo)
        }
    }

    @Test
    fun convert_zeroAndMaximumAmounts_preservesScaleWithoutOverflow() {
        BeamTransactionDirection.entries.forEach { direction ->
            listOf(0L to "0.00000000", Long.MAX_VALUE to "92233720368.54775807").forEach { (value, decimal) ->
                val record = converter.convert(transaction(direction = direction, amount = value, fee = value))
                val magnitude = BigDecimal(decimal)
                val expected = if (direction == BeamTransactionDirection.Incoming) magnitude else magnitude.negate()
                assertEquals(expected, record.mainValue.value)
                assertEquals(magnitude, record.fee.value)
            }
        }
    }

    @Test
    fun convert_negativeOrWrappedAtomicValues_rejectsMalformedTransaction() {
        BeamTransactionDirection.entries.forEach { direction ->
            listOf(-1L, Long.MIN_VALUE).forEach { invalid ->
                assertThrows(IllegalArgumentException::class.java) {
                    converter.convert(transaction(direction = direction, amount = invalid))
                }
                assertThrows(IllegalArgumentException::class.java) {
                    converter.convert(transaction(direction = direction, fee = invalid))
                }
            }
        }
    }

    @Test
    fun convert_proofHeight_preservesOnlyPositiveIntHeights() {
        val heights = listOf(
            null, Long.MIN_VALUE, -1L, 0L, 1L, Int.MAX_VALUE.toLong(), Int.MAX_VALUE + 1L, Long.MAX_VALUE,
        )
        val expected = listOf(null, null, null, null, 1, Int.MAX_VALUE, null, null)
        heights.zip(expected).forEach { (height, blockHeight) ->
            val record = converter.convert(transaction(proofHeight = height).copy(minHeight = 50L))
            assertEquals(blockHeight, record.blockHeight)
        }
    }

    @Test
    fun status_eachSdkStatus_ignoresMissingFutureAndOldProofHeights() {
        val expected = mapOf(
            BeamTransactionStatus.Pending to TransactionStatus.Pending,
            BeamTransactionStatus.InProgress to TransactionStatus.Processing(0.3f),
            BeamTransactionStatus.Registering to TransactionStatus.Processing(0.5f),
            BeamTransactionStatus.Confirming to TransactionStatus.Processing(0.8f),
            BeamTransactionStatus.Completed to TransactionStatus.Completed,
            BeamTransactionStatus.Failed to TransactionStatus.Failed,
            BeamTransactionStatus.Canceled to TransactionStatus.Failed,
            BeamTransactionStatus.Unknown to TransactionStatus.Pending,
        )
        assertEquals(BeamTransactionStatus.entries.toSet(), expected.keys)
        expected.forEach { (sdkStatus, status) ->
            listOf(null, 100L, Long.MAX_VALUE).forEach { proofHeight ->
                val record = converter.convert(transaction(status = sdkStatus, proofHeight = proofHeight))
                listOf(null, -1, 0, 99, 100, Int.MAX_VALUE).forEach { tip ->
                    assertStatus(status, record.status(tip))
                }
                assertEquals(sdkStatus, record.sdkStatus)
                assertEquals(status == TransactionStatus.Failed, record.failed)
            }
        }
    }

    @Test
    fun convert_completedTransactionReorg_preservesIdentityAndRevertsStatus() {
        val completed = transaction(status = BeamTransactionStatus.Completed, proofHeight = 100L)
        val original = converter.convert(completed)
        assertSame(TransactionStatus.Completed, original.status(1_000))
        // Per status: the two no longer share a progress value, so one shared expectation would
        // silently stop discriminating between them.
        mapOf(
            BeamTransactionStatus.Registering to 0.5f,
            BeamTransactionStatus.Confirming to 0.8f,
        ).forEach { (status, progress) ->
            val reverted = converter.convert(completed.copy(status = status))
            assertEquals(original.uid, reverted.uid)
            assertEquals(original.transactionHash, reverted.transactionHash)
            assertEquals(original.blockHeight, reverted.blockHeight)
            assertStatus(TransactionStatus.Processing(progress), reverted.status(1_000))
            assertFalse(reverted.failed)
        }
        assertSame(TransactionStatus.Completed, converter.convert(completed).status(1_000))
    }

    @Test
    fun convert_sameTransactionInDifferentAccounts_hasDistinctStableIdentity() {
        val transaction = transaction()
        val original = converter.convert(transaction)
        val refreshed = converter.convert(transaction.copy(kernelId = "new-kernel", proofHeight = 200L))
        val otherAccount = BeamTransactionRecordConverter(source("account-b"), token).convert(transaction)
        assertEquals(original.uid, refreshed.uid)
        assertNotEquals(original.uid, otherAccount.uid)
        assertEquals(transaction.id, original.transactionHash)
        assertEquals(transaction.createdAtEpochSeconds, original.timestamp)
        assertSame(source, original.source)
        assertEquals(transaction.kernelId, original.kernelId)
        assertNull(original.failureReason)
        assertEquals(2, setOf(original, refreshed, otherAccount).size)
    }

    @Test
    fun convert_delimitersInAccountAndTransactionIds_doesNotCollide() {
        val first = BeamTransactionRecordConverter(source("a:b"), token).convert(transaction().copy(id = "c"))
        val second = BeamTransactionRecordConverter(source("a"), token).convert(transaction().copy(id = "b:c"))
        assertNotEquals(first.uid, second.uid)
    }

    @Test
    fun compareTo_sameSecondTransactions_usesStableIdTieBreaker() {
        val first = converter.convert(transaction().copy(id = "a"))
        val second = converter.convert(transaction().copy(id = "b"))
        assertTrue(first < second)
        assertTrue(second > first)
    }

    @Test
    fun convert_failedTransaction_preservesFailureReasonAndOptionalKernel() {
        val record = converter.convert(
            transaction(status = BeamTransactionStatus.Failed).copy(kernelId = null, failureReason = "expired"),
        )
        assertEquals("expired", record.failureReason)
        assertNull(record.kernelId)
        assertTrue(record.failed)
    }

    private fun assertStatus(expected: TransactionStatus, actual: TransactionStatus) {
        assertEquals(expected::class, actual::class)
        if (expected is TransactionStatus.Processing && actual is TransactionStatus.Processing) {
            assertEquals(expected.progress, actual.progress, 0f)
        }
    }

    private fun source(accountId: String) = TransactionSource(
        blockchain = blockchain,
        account = mockk<Account> { every { id } returns accountId },
        meta = null,
    )

    @Test
    fun convert_counterparty_isCarriedThroughIncludingNull() {
        val endpoint = "3ZkVRpXZ7dJqQFHRzWq2SbWUzB1xJz9Mk6uQnD4Yc7Tt"
        val withCounterparty = transaction().copy(counterparty = endpoint)
        assertEquals(endpoint, converter.convert(withCounterparty).counterparty)

        // Absence must survive as absence: no empty string, no placeholder.
        assertNull(converter.convert(transaction().copy(counterparty = null)).counterparty)
    }

    private fun transaction(
        direction: BeamTransactionDirection = BeamTransactionDirection.Incoming,
        amount: Long = 123_456_789L,
        fee: Long = 100L,
        status: BeamTransactionStatus = BeamTransactionStatus.Pending,
        proofHeight: Long? = null,
    ) = BeamTransaction(
        id = "transaction-id",
        direction = direction,
        amount = amount,
        fee = fee,
        createdAtEpochSeconds = 1_700_000_000L,
        minHeight = null,
        proofHeight = proofHeight,
        kernelId = "kernel-id",
        status = status,
        failureReason = null,
    )
}
