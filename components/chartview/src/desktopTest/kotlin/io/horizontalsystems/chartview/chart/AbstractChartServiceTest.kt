package io.horizontalsystems.chartview.chart

import cash.p.terminal.core.TestDispatcherProvider
import io.horizontalsystems.chartview.ChartViewType
import io.horizontalsystems.chartview.models.ChartPoint
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.models.HsTimePeriod
import io.mockk.every
import io.mockk.mockk
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AbstractChartServiceTest {

    private val dispatcher = StandardTestDispatcher()
    private val dayItems = ChartPointsWrapper(listOf(ChartPoint(1f, 1000L)))
    private val weekItems = ChartPointsWrapper(listOf(ChartPoint(7f, 7000L)))

    @Test
    fun updateChartInterval_supersededFetchSucceedsLater_publishesOnlyNewInterval() =
        runSwitchTest(finishDay = { it.resume(dayItems) })

    @Test
    fun updateChartInterval_supersededFetchFailsLater_publishesOnlyNewInterval() =
        runSwitchTest(finishDay = { it.resumeWithException(IllegalStateException("offline")) })

    private fun runSwitchTest(finishDay: (Continuation<ChartPointsWrapper>) -> Unit) = runTest(dispatcher) {
        val service = DayBlockedChartService(this)
        val published = mutableListOf<Result<ChartPointsWrapper>>()
        service.chartPointsWrapperObservable.subscribe { published += it }

        service.start()
        runCurrent()
        service.updateChartInterval(HsTimePeriod.Week1)
        runCurrent()
        finishDay(checkNotNull(service.dayFetch))
        runCurrent()

        assertEquals(listOf(Result.success(weekItems)), published)
        service.stop()
    }

    private inner class DayBlockedChartService(scope: TestScope) :
        AbstractChartService(TestDispatcherProvider(dispatcher, scope.backgroundScope)) {
        override val chartIntervals = listOf(HsTimePeriod.Day1, HsTimePeriod.Week1)
        override val chartViewType = ChartViewType.Line
        override val initialChartInterval = HsTimePeriod.Day1
        override val currencyManager = mockk<CurrencyManager> {
            every { baseCurrency } returns Currency("USD", "$", 2)
            every { baseCurrencyUpdatedSignal } returns PublishSubject.create()
        }

        var dayFetch: Continuation<ChartPointsWrapper>? = null

        // suspendCoroutine ignores cancellation: the Day1 fetch outlives its replacement, like one already past its
        // last suspension point when the interval switches.
        override suspend fun getItems(chartInterval: HsTimePeriod, currency: Currency) = when (chartInterval) {
            HsTimePeriod.Day1 -> suspendCoroutine { dayFetch = it }
            else -> weekItems
        }
    }
}
