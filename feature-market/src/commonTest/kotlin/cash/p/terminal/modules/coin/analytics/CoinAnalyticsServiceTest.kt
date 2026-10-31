package cash.p.terminal.modules.coin.analytics

import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Single
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoinAnalyticsServiceTest {

    private val fullCoin = FullCoin(Coin("bitcoin", "Bitcoin", "BTC"), emptyList())
    private val marketKit = mockk<MarketKitWrapper>()
    private val currencyManager = mockk<CurrencyManager> {
        every { baseCurrency } returns Currency("USD", "$", 2)
    }

    @Test
    fun start_requestFails_emitsError() = runTest {
        val error = IllegalStateException("boom")
        every { marketKit.analyticsSingle(fullCoin.coin.uid, "USD") } returns Single.error(error)
        val service = CoinAnalyticsService(fullCoin, marketKit, currencyManager)

        service.start()

        val result = service.stateFlow.first()
        assertEquals(error.message, (result as DataState.Error).error.message)
    }
}
