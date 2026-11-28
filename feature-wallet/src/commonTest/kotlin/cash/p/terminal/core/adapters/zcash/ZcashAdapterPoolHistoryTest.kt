package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Pool
import cash.p.zcash.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One account backs three ZEC wallets, so each must see only the change in its own pools. */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterPoolHistoryTest : ZcashAdapterTestFixture() {

    @Test
    fun getTransactions_shieldingInTransparentWallet_showsSentToSelfWithoutFee() = runTest(dispatcher) {
        val record = records(AddressSpecType.Transparent, shielding()).single()

        assertEquals(TransactionRecordType.BITCOIN_OUTGOING, record.transactionRecordType)
        assertEquals(SHIELDED.convertZatoshiToZec().negate(), record.mainValue.decimalValue)
        assertEquals(FEE.convertZatoshiToZec(), record.fee?.decimalValue)
        assertTrue(record.sentToSelf)
    }

    @Test
    fun getTransactions_shieldingInUnifiedWallet_showsReceived() = runTest(dispatcher) {
        val record = records(AddressSpecType.Unified, shielding()).single()

        assertEquals(TransactionRecordType.BITCOIN_INCOMING, record.transactionRecordType)
        assertEquals(SHIELDED.convertZatoshiToZec(), record.mainValue.decimalValue)
        assertFalse(record.sentToSelf)
    }

    @Test
    fun getTransactions_shieldingToChangeAddressInUnifiedWallet_isListedAsIncoming() = runTest(dispatcher) {
        val records = records(
            AddressSpecType.Unified,
            shielding().copy(isChange = true),
            FilterTransactionType.Incoming,
        )

        assertEquals(1, records.size)
    }

    @Test
    fun getTransactions_unifiedToOwnTransparent_showsSentInUnifiedAndReceivedInTransparent() =
        runTest(dispatcher) {
            val transfer = transaction(mapOf(Pool.ORCHARD to -(MOVED + FEE), Pool.TRANSPARENT to MOVED))

            val sent = records(AddressSpecType.Unified, transfer).single()
            val received = records(AddressSpecType.Transparent, transfer).single()

            assertEquals(MOVED.convertZatoshiToZec().negate(), sent.mainValue.decimalValue)
            assertTrue(sent.sentToSelf)
            assertEquals(TransactionRecordType.BITCOIN_INCOMING, received.transactionRecordType)
            assertEquals(MOVED.convertZatoshiToZec(), received.mainValue.decimalValue)
        }

    @Test
    fun getTransactions_transactionOutsideWalletPools_isHidden() = runTest(dispatcher) {
        assertTrue(records(AddressSpecType.Shielded, shielding()).isEmpty())
    }

    @Test
    fun getTransactions_externalSend_unchanged() = runTest(dispatcher) {
        val record = records(AddressSpecType.Unified, transaction(mapOf(Pool.ORCHARD to -(MOVED + FEE)))).single()

        assertEquals(MOVED.convertZatoshiToZec().negate(), record.mainValue.decimalValue)
        assertFalse(record.sentToSelf)
    }

    @Test
    fun getTransactions_withoutPoolValues_keepsAccountValue() = runTest(dispatcher) {
        val record = records(AddressSpecType.Transparent, transaction(emptyMap(), value = MOVED)).single()

        assertEquals(MOVED.convertZatoshiToZec(), record.mainValue.decimalValue)
    }

    @Test
    fun getTransactions_unconfirmedShieldingInUnifiedWallet_showsReceived() = runTest(dispatcher) {
        val record = records(AddressSpecType.Unified, shielding().copy(height = 0, fee = 0)).single()

        assertEquals(SHIELDED.convertZatoshiToZec(), record.mainValue.decimalValue)
    }

    @Test
    fun getTransactions_unconfirmedShieldingInTransparentWallet_isHidden() = runTest(dispatcher) {
        assertTrue(records(AddressSpecType.Transparent, shielding().copy(height = 0, fee = 0)).isEmpty())
    }

    private fun shielding() =
        transaction(mapOf(Pool.TRANSPARENT to -(SHIELDED + FEE), Pool.ORCHARD to SHIELDED))

    private fun transaction(poolValues: Map<Pool, Long>, value: Long = poolValues.values.sum()) = Transaction(
        id = 1,
        txid = TXID,
        height = 3_612_000,
        time = 1_700_000_000L,
        value = value,
        memo = null,
        fee = FEE,
        totalReceived = poolValues.values.filter { it > 0 }.sum(),
        isChange = false,
        recipient = null,
        poolValues = poolValues,
    )

    private suspend fun TestScope.records(
        spec: AddressSpecType,
        transaction: Transaction,
        filter: FilterTransactionType = FilterTransactionType.All,
    ): List<BitcoinTransactionRecord> {
        adapter = createAdapter(addressSpecTyped = spec)
        adapter.start()
        emitSessionTransactions(listOf(transaction))
        advanceUntilIdle()
        return adapter.getTransactions(
            from = null,
            token = null,
            limit = 10,
            transactionType = filter,
            address = null,
        ).map { it as BitcoinTransactionRecord }
    }

    private companion object {
        const val TXID = "c0ffee"
        const val SHIELDED = 147_400L
        const val MOVED = 50_000L
        const val FEE = 6_000L
    }
}
