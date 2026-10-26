package io.horizontalsystems.chartview.chart

import cash.p.terminal.core.TestDispatcherProvider
import io.horizontalsystems.chartview.ChartViewType
import io.horizontalsystems.chartview.models.ChartPoint
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.models.HsTimePeriod
import io.mockk.every
import io.mockk.mockk
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import java.util.Optional
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModelTest {

    private val periods = listOf(HsTimePeriod.Day1, HsTimePeriod.Week1, null)
    private val dispatcher = StandardTestDispatcher()

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun init_serviceWithPeriods_exposesTabForEachPeriodWithInitialSelected() = runViewModelTest { viewModel, _ ->
        runCurrent()

        val tabs = viewModel.uiState.tabItems
        assertEquals(periods, tabs.map { it.item })
        assertEquals(listOf(HsTimePeriod.Day1), tabs.filter { it.selected }.map { it.item })
    }

    @Test
    fun init_serviceEmitsItems_loadingTurnsIntoSuccessWithHeader() = runViewModelTest { viewModel, service ->
        assertTrue(viewModel.uiState.loading)
        runCurrent()
        assertTrue(viewModel.uiState.loading)

        service.publishItems()
        runCurrent()

        assertFalse(viewModel.uiState.loading)
        assertNotNull(viewModel.uiState.chartHeaderView)
        assertNotNull(viewModel.uiState.chartInfoData)
    }

    private fun runViewModelTest(block: suspend TestScope.(ChartViewModel, FakeChartService) -> Unit) =
        runTest(dispatcher) {
            Dispatchers.setMain(dispatcher)
            val service = FakeChartService(periods)
            val valueFormatter = object : ChartModule.ChartNumberFormatter {
                override fun formatValue(currency: Currency, value: BigDecimal) = value.toPlainString()
            }
            val viewModel = ChartViewModel(
                service,
                valueFormatter,
                mockk<IAppNumberFormatter>(relaxed = true),
                TestDispatcherProvider(dispatcher, this),
            )
            block(viewModel, service)
        }

    // Publishes only on explicit trigger so no wall-clock ordering exists anywhere in the tests.
    private class FakeChartService(override val chartIntervals: List<HsTimePeriod?>) : AbstractChartService() {
        override val chartViewType = ChartViewType.Line
        override val currencyManager = mockk<CurrencyManager> {
            every { baseCurrency } returns Currency("USD", "$", 2)
            every { baseCurrencyUpdatedSignal } returns PublishSubject.create()
        }
        override val initialChartInterval = HsTimePeriod.Day1

        override suspend fun start() {
            chartTypeObservable.onNext(Optional.of(initialChartInterval))
        }

        fun publishItems() = chartPointsWrapperObservable.onNext(
            Result.success(ChartPointsWrapper(items = listOf(ChartPoint(1f, 1000L), ChartPoint(2f, 2000L))))
        )

        override suspend fun getItems(chartInterval: HsTimePeriod, currency: Currency) =
            ChartPointsWrapper(items = emptyList())
    }
}
