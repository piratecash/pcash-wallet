package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.featureStacking.ui.PirateInvestmentChartService
import cash.p.terminal.featureStacking.ui.StakingChartDisplayMode
import cash.p.terminal.featureStacking.ui.awaitShownIncome
import cash.p.terminal.featureStacking.ui.incomeDay
import cash.p.terminal.featureStacking.ui.pirateCoinScreen.PirateCoinChartViewModel
import cash.p.terminal.featureStacking.ui.usdRate
import cash.p.terminal.network.pirate.domain.enity.InvestmentGraphData
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.network.pirate.domain.useCase.FiatCurrencyRateService
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.staking_chart_no_fiat_estimate
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class StackingCoinChartViewModelTest {

    private val queuedMain = StandardTestDispatcher()
    private val balanceHiddenState = MutableStateFlow(false)
    private val repository = mockk<PiratePlaceRepository> {
        coEvery { getInvestmentChart(any(), any(), any()) } returns
            InvestmentGraphData(listOf(incomeDay(0, 10.0), incomeDay(1, 10.0), incomeDay(2, 10.0)))
        coEvery { getCoinPriceChart(any(), any()) } returns listOf(usdRate(0, "0.20"), usdRate(1, "0.30"))
    }
    private val fiatCurrencyRateService = mockk<FiatCurrencyRateService> {
        coEvery { usdtToCurrencyRate(any()) } returns BigDecimal.ONE
    }
    private val currencyManager = mockk<CurrencyManager> {
        every { baseCurrency } returns Currency("USD", "$", 2)
        every { baseCurrencyUpdatedSignal } returns PublishSubject.create()
    }
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true) {
        every { formatNumberShort(any(), any()) } answers { firstArg<BigDecimal>().toPlainString() }
        every { formatFiatFull(any(), any()) } answers { secondArg<String>() + firstArg<BigDecimal>().toPlainString() }
    }
    private val service = PirateInvestmentChartService(currencyManager, repository, fiatCurrencyRateService)
    private val token = mockk<Token> { every { coin } returns Coin("piratecash", "PirateCash", "PIRATE") }

    @After
    fun tearDown() {
        service.stop()
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun init_mainRunsImmediately_superConstructorStateBuildsWithoutSubclassFields() =
        runViewModelTest(UnconfinedTestDispatcher()) { viewModel ->
            assertEquals(3, viewModel.displayModeTabs.size)
        }

    @Test
    fun createState_bothMode_headerShowsCoinsAndFiatWithoutExtraData() = runViewModelTest(queuedMain) { viewModel ->
        viewModel.onSelectDisplayMode(StakingChartDisplayMode.BOTH)
        awaitIncome(StakingChartDisplayMode.BOTH)

        val header = viewModel.uiState.chartHeaderView
        assertEquals("10.0 PIRATE", header?.value)
        assertEquals("$3.000", header?.valueHint)
        assertNull(header?.extraData)
    }

    @Test
    fun createState_balanceHidden_hidesAxisLabels() = runViewModelTest(queuedMain) { viewModel ->
        balanceHiddenState.value = true
        viewModel.onSelectDisplayMode(StakingChartDisplayMode.FIAT)
        awaitIncome(StakingChartDisplayMode.FIAT)

        assertNull(viewModel.uiState.chartInfoData?.maxValue)
        assertNull(viewModel.uiState.chartInfoData?.minValue)
    }

    @Test
    fun createState_fiatModeWithoutRates_headerShowsNoEstimate() {
        coEvery { repository.getCoinPriceChart(any(), any()) } throws IllegalStateException("offline")
        runViewModelTest(queuedMain) { viewModel ->
            viewModel.onSelectDisplayMode(StakingChartDisplayMode.FIAT)
            awaitIncome(StakingChartDisplayMode.FIAT)

            val noEstimate = Translator.getString(Res.string.staking_chart_no_fiat_estimate, "USD")
            assertEquals(noEstimate, viewModel.uiState.chartHeaderView?.value)
        }
    }

    // Production Main is immediate, so createState() runs inside the ChartViewModel constructor;
    // UnconfinedTestDispatcher reproduces that, StandardTestDispatcher defers it.
    private fun runViewModelTest(
        dispatcher: TestDispatcher,
        block: suspend TestScope.(PirateCoinChartViewModel) -> Unit,
    ) = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val balanceHiddenManager = mockk<IBalanceHiddenManager> {
            every { balanceHiddenFlow } returns balanceHiddenState
        }
        startKoin { modules(module { single { balanceHiddenManager } }) }
        val viewModel = PirateCoinChartViewModel(service, numberFormatter, TestDispatcherProvider(dispatcher, this))
        runCurrent()
        viewModel.setStakingSource("address", token)
        // Let the source load land while Main is still set; a late emission would leak into the next test.
        awaitIncome(StakingChartDisplayMode.COINS)
        block(viewModel)
    }

    /** The service loads on its own IO scope; the view model then needs the test dispatcher to run. */
    private fun TestScope.awaitIncome(mode: StakingChartDisplayMode) {
        service.awaitShownIncome { it.mode == mode && it.points.isNotEmpty() }
        runCurrent()
    }
}
