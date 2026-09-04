package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.zcash.BroadcastResult
import cash.p.zcash.ZcashSdk
import cash.p.zcash.transactionId
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterBroadcastReservationTest : ZcashAdapterTestFixture() {

    @Test
    fun broadcastRawTransaction_validPayload_reservesAndRefreshesBeforeNetworkBroadcast() =
        runTest(dispatcher) {
            coEvery { zcashWallet.latestHeight() } returns HEIGHT
            coEvery { zcashWallet.broadcast(DB_ACCOUNT_ID, any(), HEIGHT, any()) } returns
                BroadcastResult(errorCode = 0, message = TX_ID)
            adapter = createAdapter()
            adapter.start()
            advanceUntilIdle()

            adapter.broadcastRawTransaction(
                RAW_HEX,
                OfflineBroadcastMetadata.Zcash(txHash = TX_ID),
            )

            coVerifyOrder {
                session.reserveForBroadcast(match { it.contentEquals(RAW) }, any())
                session.refresh()
                zcashWallet.broadcast(DB_ACCOUNT_ID, match { it.contentEquals(RAW) }, HEIGHT, any())
            }
        }

    @Test
    fun broadcastRawTransaction_noMetadata_broadcastsWithTxHashDerivedFromRawBytes() =
        runTest(dispatcher) {
            stubDerivedTransactionId()
            coEvery { zcashWallet.broadcast(DB_ACCOUNT_ID, any(), HEIGHT, any()) } returns
                BroadcastResult(errorCode = 0, message = TX_ID)
            startAdapter()

            adapter.broadcastRawTransaction(RAW_HEX, metadata = null)

            coVerifyOrder {
                session.reserveForBroadcast(match { it.contentEquals(RAW) }, any())
                session.refresh()
                zcashWallet.broadcast(DB_ACCOUNT_ID, match { it.contentEquals(RAW) }, HEIGHT, any())
            }
        }

    /** A transaction of unknown origin spends no note of ours, so ownership must not be required. */
    @Test
    fun broadcastRawTransaction_foreignTransaction_doesNotRequireOwnInputs() =
        runTest(dispatcher) {
            coEvery { zcashWallet.latestHeight() } returns HEIGHT
            coEvery { zcashWallet.broadcast(DB_ACCOUNT_ID, any(), HEIGHT, any()) } returns
                BroadcastResult(errorCode = 0, message = TX_ID)
            adapter = createAdapter()
            adapter.start()
            advanceUntilIdle()

            adapter.broadcastRawTransaction(
                RAW_HEX,
                OfflineBroadcastMetadata.Zcash(txHash = TX_ID),
            )

            coVerifyOrder {
                session.reserveForBroadcast(any(), requireOwnInputs = false)
                zcashWallet.broadcast(DB_ACCOUNT_ID, any(), HEIGHT, requireOwnInputs = false)
            }
        }

    /** The only branch where the requested hash reaches the result: on acceptance the node names it. */
    @Test
    fun broadcastRawTransaction_noMetadataAndNodeReportsAlreadyCommitted_returnsDerivedTxHash() =
        runTest(dispatcher) {
            stubDerivedTransactionId()
            coEvery { zcashWallet.broadcast(DB_ACCOUNT_ID, any(), HEIGHT, any()) } returns
                BroadcastResult(errorCode = -1, message = ALREADY_COMMITTED)
            startAdapter()

            val result = adapter.broadcastRawTransaction(RAW_HEX, metadata = null)

            assertEquals(BroadcastRawTransactionStatus.AlreadyKnown, result.status)
            assertEquals(DERIVED_TX_ID, result.txHash)
        }

    /** Uppercase proves the derived id is canonicalized the same way the signer's is. */
    private fun stubDerivedTransactionId() {
        mockkStatic("cash.p.zcash.ZcashSdkKt")
        coEvery {
            ZcashSdk.transactionId(match { it.contentEquals(RAW) })
        } returns DERIVED_TX_ID.uppercase()
    }

    private fun TestScope.startAdapter() {
        coEvery { zcashWallet.latestHeight() } returns HEIGHT
        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()
    }

    private companion object {
        const val HEIGHT = 3_428_200
        const val RAW_HEX = "deadbeef000102030405"
        val RAW = byteArrayOf(
            0xde.toByte(),
            0xad.toByte(),
            0xbe.toByte(),
            0xef.toByte(),
            0,
            1,
            2,
            3,
            4,
            5,
        )
        const val TX_ID =
            "b7c4a1f0d3e2b5a698877665544332211ffeeddccbbaa99887766554433221100"
        const val DERIVED_TX_ID =
            "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
        const val ALREADY_COMMITTED =
            "any transaction with the same effects will be rejected from the mempool " +
                "until a chain reset: transaction was committed to the best chain"
    }
}
