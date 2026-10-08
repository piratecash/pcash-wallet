package cash.p.terminal.wallet

import cash.p.terminal.wallet.syncers.CoinSyncer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MarketKitSyncTest {

    @Test
    fun sync_coinSyncerThrows_doesNotPropagate() = runTest {
        val coinSyncer = mockk<CoinSyncer> {
            coEvery { sync(any()) } throws IllegalStateException("database or disk is full")
        }
        val marketKit = MarketKit(
            nftManager = mockk(),
            marketOverviewManager = mockk(),
            coinManager = mockk(),
            coinSyncer = coinSyncer,
            coinPriceManager = mockk(),
            coinHistoricalPriceManager = mockk(),
            coinPriceSyncManager = mockk(),
            postManager = mockk(),
            globalMarketInfoManager = mockk(),
            hsProvider = mockk(),
        )

        marketKit.sync(forceUpdate = false)
    }
}
