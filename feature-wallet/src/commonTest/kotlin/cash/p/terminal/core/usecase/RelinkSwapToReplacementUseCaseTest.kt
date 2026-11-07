package cash.p.terminal.core.usecase

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class RelinkSwapToReplacementUseCaseTest {

    private companion object {
        const val UID_A = "uid-A"
        const val UID_B = "uid-B"
        const val HASH_A = "aaaa"
        const val HASH_B = "bbbb"
        const val SWAP_DATE = 1_000L
        val CHAIN = listOf(HASH_A, HASH_B)
    }

    private val swapStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
        every { getByOutgoingRecordUid(any()) } returns null
    }
    private val multiSwapStorage = mockk<PendingMultiSwapStorage>(relaxed = true)
    private val useCase = RelinkSwapToReplacementUseCase(
        swapStorage,
        multiSwapStorage,
        UnconfinedTestDispatcher().let { TestDispatcherProvider(it, TestScope(it)) },
    )

    @Test
    fun relink_changellyProviderId_movesOnlyUid() = runTest {
        storedOn(UID_A, swap(transactionId = "changelly-order-id"))

        relinkTo(UID_B, HASH_B)

        verify { swapStorage.relinkOutgoingRecord(SWAP_DATE, UID_B, "changelly-order-id", null) }
    }

    @Test
    fun relink_depositHashProvider_movesUidAndBothHashes() = runTest {
        storedOn(UID_A, swap(transactionId = HASH_A, depositTransactionHash = "0x${HASH_A.uppercase()}"))

        val relinked = relinkTo(UID_B, HASH_B)

        verify { swapStorage.relinkOutgoingRecord(SWAP_DATE, UID_B, HASH_B, HASH_B) }
        assertEquals(UID_B, relinked?.outgoingRecordUid)
    }

    @Test
    fun relink_noSwapOnCandidates_doesNotUpdate() = runTest {
        val relinked = relinkTo(UID_B, HASH_B)

        assertNull(relinked)
        verify(exactly = 0) { swapStorage.relinkOutgoingRecord(any(), any(), any(), any()) }
    }

    @Test
    fun relink_alreadyRelinked_doesNotUpdate() = runTest {
        storedOn(UID_B, swap(transactionId = HASH_B, depositTransactionHash = HASH_B, outgoingRecordUid = UID_B))

        relinkTo(UID_B, HASH_B)

        verify(exactly = 0) { swapStorage.relinkOutgoingRecord(any(), any(), any(), any()) }
    }

    @Test
    fun relink_historyMovedUidButHashesStale_fixesHashes() = runTest {
        storedOn(UID_B, swap(transactionId = HASH_A, depositTransactionHash = HASH_A, outgoingRecordUid = UID_B))

        relinkTo(UID_B, HASH_B)

        verify { swapStorage.relinkOutgoingRecord(SWAP_DATE, UID_B, HASH_B, HASH_B) }
    }

    @Test
    fun relink_originalMinedAfterAll_movesUidAndHashesBack() = runTest {
        storedOn(UID_B, swap(transactionId = HASH_B, depositTransactionHash = HASH_B, outgoingRecordUid = UID_B))

        relinkTo(UID_A, HASH_A)

        verify { swapStorage.relinkOutgoingRecord(SWAP_DATE, UID_A, HASH_A, HASH_A) }
    }

    @Test
    fun relink_multiSwapFirstLegOnOriginal_movesLeg1Uids() = runTest {
        relinkTo(UID_B, HASH_B)

        coVerify { multiSwapStorage.moveLeg1RecordUid(listOf(UID_A), UID_B) }
    }

    private suspend fun relinkTo(uid: String, hash: String) = useCase.relink(
        candidateUids = listOf(UID_A, UID_B),
        chainHashes = CHAIN,
        toUid = uid,
        toHash = hash,
    )

    private fun storedOn(uid: String, swap: SwapProviderTransaction) {
        every { swapStorage.getByOutgoingRecordUid(uid) } returns swap
    }

    private fun swap(
        transactionId: String,
        depositTransactionHash: String? = null,
        outgoingRecordUid: String = UID_A,
    ) = SwapProviderTransaction(
        date = SWAP_DATE,
        outgoingRecordUid = outgoingRecordUid,
        transactionId = transactionId,
        status = "waiting",
        provider = SwapProvider.PCASH_BACKEND,
        coinUidIn = "bitcoin",
        blockchainTypeIn = "bitcoin",
        amountIn = BigDecimal("0.01"),
        addressIn = "bc1-deposit",
        coinUidOut = "tether",
        blockchainTypeOut = "tron",
        amountOut = BigDecimal("1000"),
        addressOut = "T-recipient",
        depositTransactionHash = depositTransactionHash,
    )
}
