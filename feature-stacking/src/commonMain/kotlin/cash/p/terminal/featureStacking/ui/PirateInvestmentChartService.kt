package cash.p.terminal.featureStacking.ui

import cash.p.terminal.network.data.entity.ChartPeriod
import cash.p.terminal.network.pirate.domain.enity.PricePoint
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.network.pirate.domain.useCase.FiatCurrencyRateService
import co.touchlab.kermit.Logger
import io.horizontalsystems.chartview.ChartViewType
import io.horizontalsystems.chartview.chart.AbstractChartService
import io.horizontalsystems.chartview.chart.ChartPointsWrapper
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.models.HsTimePeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal

class PirateInvestmentChartService(
    override val currencyManager: CurrencyManager,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val fiatCurrencyRateService: FiatCurrencyRateService,
) : AbstractChartService() {

    private val logger = Logger.withTag("PirateInvestmentChartService")

    private var investCode: String = ""
    private var coinUid: String = ""
    private var receiveAddress: String = ""

    @Volatile
    var displayMode = StakingChartDisplayMode.COINS
        private set

    @Volatile
    var shownIncome: StakingIncomeSnapshot? = null
        private set

    // Exactly what is shown, even if partial: mode and currency changes reuse it, an interval switch reloads.
    @Volatile
    private var loadedIncome: Pair<HsTimePeriod, List<StakingIncomePoint>>? = null

    fun setData(investCode: String, coinUid: String, address: String) {
        if (investCode != this.investCode || coinUid != this.coinUid || address != receiveAddress) {
            this.investCode = investCode
            this.coinUid = coinUid
            receiveAddress = address
            invalidate()
        }
        refresh()
    }

    fun setDisplayMode(mode: StakingChartDisplayMode) {
        displayMode = mode
        refresh()
    }

    fun invalidate() {
        loadedIncome = null
    }

    override val chartIntervals: List<HsTimePeriod> = listOf(
        HsTimePeriod.Day1,
        HsTimePeriod.Week1,
        HsTimePeriod.Month1,
        HsTimePeriod.Year1
    )

    override val initialChartInterval: HsTimePeriod = chartIntervals.first()

    override val chartViewType: ChartViewType = ChartViewType.Line

    override suspend fun getItems(
        chartInterval: HsTimePeriod,
        currency: Currency
    ): ChartPointsWrapper {
        if (receiveAddress.isEmpty()) return ChartPointsWrapper(emptyList())

        val usdIncome = loadedIncome?.takeIf { it.first == chartInterval }?.second ?: loadUsdIncome(chartInterval)
        // Same conversion as the coin price chart; no fallback to 1, that would show dollars as another currency.
        val usdToCurrency = fiatCurrencyRateService.usdtToCurrencyRate(currency.code)
        val points = usdIncome.withFiatMultiplier(usdToCurrency)
        val mode = displayMode
        shownIncome = StakingIncomeSnapshot(currency, mode, points)
        return ChartPointsWrapper(points.toChartPoints(mode))
    }

    private suspend fun loadUsdIncome(interval: HsTimePeriod): List<StakingIncomePoint> = coroutineScope {
        val buckets = async { loadBuckets(interval) }
        val days = if (interval == HsTimePeriod.Day1) {
            null
        } else {
            async { orNullLogged { loadBuckets(HsTimePeriod.Day1) } }
        }
        val rates = async { orNullLogged { loadDailyUsdRates() } }

        val bucketPoints = buckets.await()
        val dayPoints = if (days == null) bucketPoints else days.await()
        val usdRates = rates.await()

        valueIncome(bucketPoints.trimmed(interval), dayPoints, usdRates.orEmpty())
            .also { loadedIncome = interval to it }
    }

    private suspend fun loadBuckets(interval: HsTimePeriod): List<PricePoint> =
        piratePlaceRepository.getInvestmentChart(
            coinGeckoUid = investCode,
            address = receiveAddress,
            period = interval.toChartPeriod()
        ).points

    private suspend fun loadDailyUsdRates(): Map<Long, BigDecimal> =
        // MAX, not YEAR: income older than a year is valued too.
        dailyRates(piratePlaceRepository.getCoinPriceChart(coinUid, ChartPeriod.MAX))

    /** Missing days or rates only cost the fiat estimate; the coin chart must still load. */
    private suspend fun <T> orNullLogged(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Staking income fiat data unavailable" }
        null
    }

    private fun HsTimePeriod.toChartPeriod() = when (this) {
        HsTimePeriod.Day1 -> ChartPeriod.DAY
        HsTimePeriod.Week1 -> ChartPeriod.WEEK
        HsTimePeriod.Month1 -> ChartPeriod.MONTH
        HsTimePeriod.Year1 -> ChartPeriod.YEAR
        else -> ChartPeriod.DAY
    }

    private fun List<PricePoint>.trimmed(interval: HsTimePeriod) = when (interval) {
        HsTimePeriod.Year1 -> this
        else -> dropLast(1)
    }
}
