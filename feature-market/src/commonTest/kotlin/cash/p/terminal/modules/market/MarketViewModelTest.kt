package cash.p.terminal.modules.market

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.market.MarketModule.Tab
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.models.MarketGlobal
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.MarketSettingsStore
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class MarketViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))

    private val usd = Currency("USD", "$", 2)
    private val eur = Currency("EUR", "€", 2)
    private val usdGlobal = marketGlobal(BigDecimal(100))
    private val eurGlobal = marketGlobal(BigDecimal(90))

    private var baseCurrency = usd
    private val currencyUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val currencyManager = mockk<CurrencyManager> {
        every { this@mockk.baseCurrency } answers { this@MarketViewModelTest.baseCurrency }
        every { baseCurrencyUpdatedFlow } returns currencyUpdates
    }
    private val settings = mockk<MarketSettingsStore>(relaxed = true) {
        every { launchPage } returns null
        every { currentMarketTab } returns null
    }
    private val marketKit = mockk<MarketKitWrapper>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsMarketGlobalForBaseCurrency() {
        every { marketKit.marketGlobalSingle("USD") } returns Single.just(usdGlobal)

        val viewModel = createViewModel()

        assertEquals(usdGlobal, viewModel.uiState.marketGlobal)
        assertEquals(usd, viewModel.uiState.currency)
    }

    @Test
    fun currencyChange_requestsMarketGlobalWithNewCode() = runTest {
        every { marketKit.marketGlobalSingle("USD") } returns Single.just(usdGlobal)
        every { marketKit.marketGlobalSingle("EUR") } returns Single.just(eurGlobal)
        val viewModel = createViewModel()

        changeCurrency(eur)

        verify(exactly = 1) { marketKit.marketGlobalSingle("EUR") }
        assertEquals(eurGlobal, viewModel.uiState.marketGlobal)
        assertEquals(eur, viewModel.uiState.currency)
    }

    @Test
    fun currencyChange_whileUsdRequestPending_staleUsdReplyDoesNotOverwriteEur() = runTest {
        val usdReply = SingleSubject.create<MarketGlobal>()
        val eurReply = SingleSubject.create<MarketGlobal>()
        every { marketKit.marketGlobalSingle("USD") } returns usdReply
        every { marketKit.marketGlobalSingle("EUR") } returns eurReply
        val viewModel = createViewModel()
        assertTrue(usdReply.hasObservers())

        changeCurrency(eur)
        assertFalse(usdReply.hasObservers())
        eurReply.onSuccess(eurGlobal)
        usdReply.onSuccess(usdGlobal)

        assertEquals(eurGlobal, viewModel.uiState.marketGlobal)
        assertEquals(eur, viewModel.uiState.currency)
    }

    @Test
    fun currencyChange_afterUsdLoaded_eurPending_doesNotPairUsdDataWithEur() = runTest {
        every { marketKit.marketGlobalSingle("USD") } returns Single.just(usdGlobal)
        every { marketKit.marketGlobalSingle("EUR") } returns SingleSubject.create<MarketGlobal>()
        val viewModel = createViewModel()

        changeCurrency(eur)

        assertEquals(eur, viewModel.uiState.currency)
        assertNull(viewModel.uiState.marketGlobal)
    }

    @Test
    fun currencyChange_afterUsdLoaded_eurFails_marketGlobalStaysNull() = runTest {
        every { marketKit.marketGlobalSingle("USD") } returns Single.just(usdGlobal)
        every { marketKit.marketGlobalSingle("EUR") } returns Single.error(IllegalStateException("boom"))
        val viewModel = createViewModel()

        changeCurrency(eur)

        assertEquals(eur, viewModel.uiState.currency)
        assertNull(viewModel.uiState.marketGlobal)
    }

    @Test
    fun onSelect_persistsTabInSettings() {
        every { marketKit.marketGlobalSingle("USD") } returns Single.just(usdGlobal)
        val viewModel = createViewModel()

        viewModel.onSelect(Tab.Posts)

        verify { settings.currentMarketTab = "Posts" }
        assertEquals(Tab.Posts, viewModel.uiState.selectedTab)
    }

    @Test
    fun init_savedTab_restoresTab() {
        every { marketKit.marketGlobalSingle("USD") } returns Single.just(usdGlobal)
        every { settings.currentMarketTab } returns "Pairs"

        val viewModel = createViewModel()

        assertEquals(Tab.Pairs, viewModel.uiState.selectedTab)
    }

    @Test
    fun init_watchlistLaunchPage_overridesSavedTab() {
        every { marketKit.marketGlobalSingle("USD") } returns Single.just(usdGlobal)
        every { settings.currentMarketTab } returns "Pairs"
        every { settings.launchPage } returns "Watchlist"

        val viewModel = createViewModel()

        assertEquals(Tab.Watchlist, viewModel.uiState.selectedTab)
    }

    private fun createViewModel() =
        MarketViewModel(settings, marketKit, currencyManager, dispatcherProvider)

    private suspend fun changeCurrency(currency: Currency) {
        baseCurrency = currency
        currencyUpdates.emit(Unit)
    }

    private fun marketGlobal(marketCap: BigDecimal) = MarketGlobal(
        marketCap = marketCap,
        marketCapChange = null,
        defiMarketCap = null,
        defiMarketCapChange = null,
        volume = null,
        volumeChange = null,
        btcDominance = null,
        btcDominanceChange = null,
        tvl = null,
        tvlChange = null,
        etfTotalInflow = null,
        etfDailyInflow = null
    )
}
