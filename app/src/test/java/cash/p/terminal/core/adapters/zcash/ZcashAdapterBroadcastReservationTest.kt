package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.zcash.BroadcastResult
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterBroadcastReservationTest : ZcashAdapterTestFixture() {

    @Test
    fun broadcastRawTransaction_validPayload_reservesAndRefreshesBeforeNetworkBroadcast() =
        runTest(dispatcher) {
            coEvery { zcashWallet.latestHeight() } returns HEIGHT
            coEvery { zcashWallet.broadcast(DB_ACCOUNT_ID, any(), HEIGHT) } returns
                BroadcastResult(errorCode = 0, message = TX_ID)
            adapter = createAdapter()
            adapter.start()
            advanceUntilIdle()

            adapter.broadcastRawTransaction(
                RAW_HEX,
                OfflineBroadcastMetadata.Zcash(txHash = TX_ID),
            )

            coVerifyOrder {
                session.reserveForBroadcast(match { it.contentEquals(RAW) })
                session.refresh()
                zcashWallet.broadcast(DB_ACCOUNT_ID, match { it.contentEquals(RAW) }, HEIGHT)
            }
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
    }
}
