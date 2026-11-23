package cash.p.terminal.featureStacking.ui

import cash.p.terminal.network.pirate.domain.enity.CoinPriceChart
import cash.p.terminal.network.pirate.domain.enity.PricePoint
import io.horizontalsystems.chartview.models.ChartPoint
import io.horizontalsystems.core.entities.Currency
import java.math.BigDecimal

enum class StakingChartDisplayMode { COINS, FIAT, BOTH }

data class StakingIncomePoint(
    val timestampSec: Long,
    val coins: BigDecimal,
    val fiat: BigDecimal?,
)

/** Points of the last emission together with the currency their [StakingIncomePoint.fiat] is in. */
data class StakingIncomeSnapshot(
    val currency: Currency,
    val mode: StakingChartDisplayMode,
    val points: List<StakingIncomePoint>,
)

private const val MILLIS_IN_DAY = 86_400_000L

private fun epochDay(timestampMillis: Long): Long = Math.floorDiv(timestampMillis, MILLIS_IN_DAY)

/** One rate per UTC day; the earliest point wins so today's rate does not drift intraday. */
fun dailyRates(rates: List<CoinPriceChart>): Map<Long, BigDecimal> = rates
    .sortedBy { it.timestamp }
    .groupBy({ epochDay(it.timestamp) }, { it.price })
    .mapValues { it.value.first() }

/** [days] is `null` when the daily series is unavailable: fiat is then unknown, not zero. */
fun valueIncome(
    buckets: List<PricePoint>,
    days: List<PricePoint>?,
    rates: Map<Long, BigDecimal>,
): List<StakingIncomePoint> = buckets.map { bucket ->
    StakingIncomePoint(
        timestampSec = bucket.from / 1000,
        coins = bucket.value.toBigDecimal(),
        fiat = days?.let { bucket.fiatFrom(it, rates) },
    )
}

/** `null` unless the days add up to the bucket: the two series are separate requests and may disagree. */
private fun PricePoint.fiatFrom(days: List<PricePoint>, rates: Map<Long, BigDecimal>): BigDecimal? {
    val incomeDays = days.filter { covers(it) && it.value > 0 }
    val coveredCoins = incomeDays.fold(BigDecimal.ZERO) { sum, day -> sum + day.value.toBigDecimal() }
    if (coveredCoins.compareTo(value.toBigDecimal()) != 0) return null
    return incomeDays.sumFiat(rates)
}

private fun PricePoint.covers(day: PricePoint) = day.from == from || day.from in from until to

/** `null` when any income day has no rate: a partial sum would understate the bucket. */
private fun List<PricePoint>.sumFiat(rates: Map<Long, BigDecimal>): BigDecimal? {
    var sum = BigDecimal.ZERO
    for (day in this) {
        val rate = rates[epochDay(day.from)] ?: return null
        sum += day.value.toBigDecimal() * rate
    }
    return sum
}

fun List<StakingIncomePoint>.withFiatMultiplier(multiplier: BigDecimal?): List<StakingIncomePoint> =
    map { it.copy(fiat = multiplier?.let { m -> it.fiat?.multiply(m) }) }

fun List<StakingIncomePoint>.toChartPoints(mode: StakingChartDisplayMode): List<ChartPoint> = when (mode) {
    StakingChartDisplayMode.COINS -> map { ChartPoint(it.coins.toFloat(), it.timestampSec) }
    StakingChartDisplayMode.FIAT -> mapNotNull { point ->
        point.fiat?.let { ChartPoint(it.toFloat(), point.timestampSec) }
    }
    StakingChartDisplayMode.BOTH -> map {
        ChartPoint(it.coins.toFloat(), it.timestampSec, dominance = it.fiat?.toFloat())
    }
}
