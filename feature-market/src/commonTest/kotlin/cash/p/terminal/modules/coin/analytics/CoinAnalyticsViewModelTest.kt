package cash.p.terminal.modules.coin.analytics

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.AnalyticsViewItem
import cash.p.terminal.resources.CoinAnalytics_CexVolume
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.models.Analytics
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Currency
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class CoinAnalyticsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))

    private val states = MutableSharedFlow<DataState<Analytics>>(replay = 1)
    private val service = mockk<CoinAnalyticsService> {
        every { fullCoin } returns FullCoin(Coin("bitcoin", "Bitcoin", "BTC"), emptyList())
        every { currency } returns Currency("USD", "$", 2)
        every { stateFlow } returns states
        coEvery { start() } just Runs
        coEvery { refresh() } just Runs
    }
    private val numberFormatter = mockk<IAppNumberFormatter>()
    private val adviceFactory = mockk<TechnicalAdviceViewItemFactory>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } answers { firstArg<StringResource>().key }
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
        Dispatchers.resetMain()
    }

    @Test
    fun init_serviceStillLoading_showsLoading() {
        val viewModel = createViewModel()
        states.tryEmit(DataState.Loading)

        assertEquals(ViewState.Loading, viewModel.uiState.viewState)
    }

    @Test
    fun stateFlow_error_showsErrorState() {
        val viewModel = createViewModel()
        val error = IllegalStateException("boom")

        states.tryEmit(DataState.Error(error))

        assertEquals(error, (viewModel.uiState.viewState as ViewState.Error).t)
    }

    @Test
    fun stateFlow_analytics_mapsCexVolumeBlockWithSumAndRank() {
        every { numberFormatter.formatFiatShort(BigDecimal(15), "$", 2) } returns "$15"
        val viewModel = createViewModel()
        val cexVolume = Analytics.ExVolume(
            rank30d = 5,
            rating = null,
            points = listOf(
                Analytics.VolumePoint(BigDecimal(10), 1000),
                Analytics.VolumePoint(BigDecimal(5), 2000),
            ),
        )

        states.tryEmit(DataState.Success(analytics(cexVolume)))

        val block = (viewModel.uiState.viewItem as AnalyticsViewItem.Analytics).blocks.single()
        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(Res.string.CoinAnalytics_CexVolume, block.title)
        assertEquals("$15", block.value)
        assertEquals("Coin_Analytics_Last30d", block.valuePeriod)
        val rank = block.footerItems.single() as CoinAnalyticsModule.FooterType.FooterItem
        assertEquals("#5", (rank.value as CoinAnalyticsModule.BoxItem.Value).text)
    }

    @Test
    fun stateFlow_analyticsWithoutData_showsNoData() {
        val viewModel = createViewModel()

        states.tryEmit(DataState.Success(analytics()))

        assertEquals(AnalyticsViewItem.NoData, viewModel.uiState.viewItem)
    }

    @Test
    fun refresh_requestsServiceRefresh() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify { service.refresh() }
    }

    private fun createViewModel() = CoinAnalyticsViewModel(
        service = service,
        numberFormatter = numberFormatter,
        technicalAdviceViewItemFactory = adviceFactory,
        code = "BTC",
        dispatcherProvider = dispatcherProvider,
    )

    private fun analytics(cexVolume: Analytics.ExVolume? = null) = Analytics(
        cexVolume = cexVolume,
        dexVolume = null,
        dexLiquidity = null,
        addresses = null,
        transactions = null,
        revenue = null,
        fee = null,
        tvl = null,
        reports = null,
        fundsInvested = null,
        treasuries = null,
        holders = null,
        holdersRank = null,
        holdersRating = null,
    )
}
