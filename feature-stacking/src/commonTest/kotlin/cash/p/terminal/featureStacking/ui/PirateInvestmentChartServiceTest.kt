package cash.p.terminal.featureStacking.ui

import cash.p.terminal.network.data.entity.ChartPeriod
import cash.p.terminal.network.pirate.domain.enity.InvestmentGraphData
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.network.pirate.domain.useCase.FiatCurrencyRateService
import io.horizontalsystems.chartview.chart.ChartPointsWrapper
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.models.HsTimePeriod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class PirateInvestmentChartServiceTest {

    private val usd = Currency("USD", "$", 2)
    private val rub = Currency("RUB", "₽", 2)
    private val currencySignal = PublishSubject.create<Unit>()
    private val currencyManager = mockk<CurrencyManager> {
        every { baseCurrency } returns usd
        every { baseCurrencyUpdatedSignal } returns currencySignal
    }
    private val repository = mockk<PiratePlaceRepository>()
    private val fiatCurrencyRateService = mockk<FiatCurrencyRateService>()
    private val service = PirateInvestmentChartService(currencyManager, repository, fiatCurrencyRateService)

    @Before
    fun setUp() {
        // DAY trims the last (incomplete) bucket, so day 2 is never shown.
        coEvery { repository.getInvestmentChart(any(), any(), any()) } returns
            InvestmentGraphData(listOf(incomeDay(0, 10.0), incomeDay(1, 10.0), incomeDay(2, 10.0)))
        coEvery { repository.getCoinPriceChart(any(), any()) } returns listOf(usdRate(0, "0.20"), usdRate(1, "0.30"))
        coEvery { fiatCurrencyRateService.usdtToCurrencyRate("USD") } returns BigDecimal.ONE
        coEvery { fiatCurrencyRateService.usdtToCurrencyRate("RUB") } returns BigDecimal(100)
        runBlocking { service.start() }
    }

    @After
    fun tearDown() {
        service.stop()
    }

    @Test
    fun setDisplayMode_afterFirstLoad_doesNotReloadFromRepository() {
        loadIncome()

        service.setDisplayMode(StakingChartDisplayMode.FIAT)
        val fiat = service.awaitShownIncome { it.mode == StakingChartDisplayMode.FIAT }

        assertEquals(listOf(2f, 3f), fiat.items.map { it.value })
        coVerify(exactly = 1) { repository.getInvestmentChart(any(), any(), any()) }
        coVerify(exactly = 1) { repository.getCoinPriceChart(any(), any()) }
    }

    @Test
    fun invalidate_thenRefresh_reloadsFromRepository() {
        loadIncome()

        coEvery { repository.getInvestmentChart(any(), any(), any()) } returns
            InvestmentGraphData(listOf(incomeDay(0, 20.0), incomeDay(1, 20.0), incomeDay(2, 20.0)))
        service.invalidate()
        service.refresh()

        service.awaitShownIncome { it.points.first().coins == BigDecimal("20.0") }
        coVerify(exactly = 2) { repository.getInvestmentChart(any(), any(), any()) }
    }

    @Test
    fun getItems_ratesUnavailable_keepsCoinsAndDropsFiatPoints() {
        coEvery { repository.getCoinPriceChart(any(), any()) } throws IllegalStateException("offline")
        loadIncome()

        service.setDisplayMode(StakingChartDisplayMode.FIAT)
        val fiat = service.awaitShownIncome { it.mode == StakingChartDisplayMode.FIAT }

        assertTrue(fiat.items.isEmpty())
        assertEquals(listOf(BigDecimal("10.0"), BigDecimal("10.0")), service.shownIncome?.points?.map { it.coins })
    }

    @Test
    fun getItems_weekWithDailySeriesUnavailable_noFiat() {
        coEvery { repository.getInvestmentChart(any(), any(), ChartPeriod.DAY) } throws IllegalStateException("offline")
        coEvery { repository.getInvestmentChart(any(), any(), ChartPeriod.WEEK) } returns
            InvestmentGraphData(listOf(incomeBucket(0, 7, 70.0), incomeBucket(7, 14, 10.0)))
        service.setData(investCode = "PIRATE", coinUid = "piratecash", address = "address")
        service.updateChartInterval(HsTimePeriod.Week1)

        val income = service.awaitShownIncome { it.points.singleOrNull()?.coins == BigDecimal("70.0") }

        assertNull(service.shownIncome?.points?.single()?.fiat)
        assertTrue(income.items.isNotEmpty())
    }

    @Test
    fun getItems_intervalLoadedAfterAccrual_valuesFreshDailyIncome() {
        loadIncome()
        coEvery { repository.getInvestmentChart(any(), any(), ChartPeriod.DAY) } returns
            InvestmentGraphData(listOf(incomeDay(0, 20.0), incomeDay(1, 20.0)))
        coEvery { repository.getInvestmentChart(any(), any(), ChartPeriod.WEEK) } returns
            InvestmentGraphData(listOf(incomeBucket(0, 7, 40.0), incomeBucket(7, 14, 0.0)))

        service.updateChartInterval(HsTimePeriod.Week1)
        service.awaitShownIncome { it.points.singleOrNull()?.coins == BigDecimal("40.0") }

        assertEquals(0, BigDecimal(10).compareTo(service.shownIncome?.points?.single()?.fiat))
    }

    @Test
    fun updateChartInterval_backToLoadedInterval_reloadsFreshIncome() {
        loadIncome()
        coEvery { repository.getInvestmentChart(any(), any(), ChartPeriod.WEEK) } returns
            InvestmentGraphData(listOf(incomeBucket(0, 7, 70.0), incomeBucket(7, 14, 0.0)))
        service.updateChartInterval(HsTimePeriod.Week1)
        service.awaitShownIncome { it.points.singleOrNull()?.coins == BigDecimal("70.0") }

        coEvery { repository.getInvestmentChart(any(), any(), ChartPeriod.DAY) } returns
            InvestmentGraphData(listOf(incomeDay(0, 20.0), incomeDay(1, 20.0), incomeDay(2, 20.0)))
        service.updateChartInterval(HsTimePeriod.Day1)

        service.awaitShownIncome { it.points.firstOrNull()?.coins == BigDecimal("20.0") }
    }

    @Test
    fun updateChartInterval_afterPartialLoadOfOtherInterval_reloadsFreshIncome() {
        loadIncome()
        coEvery { repository.getCoinPriceChart(any(), any()) } throws IllegalStateException("offline")
        coEvery { repository.getInvestmentChart(any(), any(), ChartPeriod.WEEK) } returns
            InvestmentGraphData(listOf(incomeBucket(0, 7, 70.0), incomeBucket(7, 14, 0.0)))
        service.updateChartInterval(HsTimePeriod.Week1)
        service.awaitShownIncome { it.points.singleOrNull()?.coins == BigDecimal("70.0") }

        coEvery { repository.getInvestmentChart(any(), any(), ChartPeriod.DAY) } returns
            InvestmentGraphData(listOf(incomeDay(0, 20.0), incomeDay(1, 20.0), incomeDay(2, 20.0)))
        service.updateChartInterval(HsTimePeriod.Day1)

        service.awaitShownIncome { it.points.firstOrNull()?.coins == BigDecimal("20.0") }
    }

    @Test
    fun getItems_currencyRateUnavailable_noFiat() {
        coEvery { fiatCurrencyRateService.usdtToCurrencyRate("USD") } returns null
        loadIncome()

        assertTrue(service.shownIncome?.points.orEmpty().all { it.fiat == null })
    }

    @Test
    fun baseCurrencyChange_appliesNewRateWithoutRepositoryCall() {
        loadIncome()

        every { currencyManager.baseCurrency } returns rub
        currencySignal.onNext(Unit)
        service.awaitShownIncome { it.currency == rub }

        assertEquals(listOf(200, 300), service.shownIncome?.points?.map { it.fiat?.toInt() })
        coVerify(exactly = 1) { repository.getInvestmentChart(any(), any(), any()) }
    }

    private fun loadIncome() {
        service.setData(investCode = "PIRATE", coinUid = "piratecash", address = "address")
        service.awaitShownIncome { it.points.isNotEmpty() }
    }

}

/** The snapshot is published just before its wrapper, so the replayed wrapper may still be the previous one. */
internal fun PirateInvestmentChartService.awaitShownIncome(
    condition: (StakingIncomeSnapshot) -> Boolean,
): ChartPointsWrapper = chartPointsWrapperObservable
    .filter { result ->
        val snapshot = shownIncome ?: return@filter false
        val expected = snapshot.points.toChartPoints(snapshot.mode).map { it.value to it.dominance }
        condition(snapshot) && result.getOrNull()?.items?.map { it.value to it.dominance } == expected
    }
    .timeout(5, TimeUnit.SECONDS)
    .blockingFirst()
    .getOrThrow()
