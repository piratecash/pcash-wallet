package cash.p.terminal.featureStacking.ui

import cash.p.terminal.network.pirate.domain.enity.CoinPriceChart
import cash.p.terminal.network.pirate.domain.enity.PricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class StakingIncomeValuationTest {

    @Test
    fun valueIncome_acceptanceExample_usesRateOfEachDay() {
        val days = listOf(incomeDay(0, 10.0), incomeDay(1, 10.0))
        val rates = dailyRates(listOf(usdRate(0, "0.20"), usdRate(1, "0.30")))

        val fiat = valueIncome(days, days, rates).map { it.fiat }

        assertAmount("2", fiat[0])
        assertAmount("3", fiat[1])
    }

    @Test
    fun valueIncome_currentRateChanges_pastPointsUnchanged() {
        val days = listOf(incomeDay(0, 10.0), incomeDay(1, 10.0))
        val before = valueIncome(days, days, dailyRates(listOf(usdRate(0, "0.20"), usdRate(1, "0.30"))))

        val withIntraday = listOf(usdRate(0, "0.20"), usdRate(1, "0.30"), usdRate(1, "0.90", hourOffset = 5))
        val after = valueIncome(days, days, dailyRates(withIntraday))

        assertEquals(before, after)
    }

    @Test
    fun valueIncome_weekBucket_sumsDailyIncomeTimesDailyRate() {
        val days = listOf(incomeDay(0, 10.0), incomeDay(1, 5.0), incomeDay(7, 100.0))
        val rates = dailyRates(listOf(usdRate(0, "0.20"), usdRate(1, "0.40"), usdRate(7, "1")))

        val week = valueIncome(listOf(incomeBucket(fromDay = 0, toDay = 7, value = 15.0)), days, rates).single()

        assertAmount("4", week.fiat)
        assertAmount("15", week.coins)
    }

    @Test
    fun valueIncome_dayWithoutRate_bucketHasNoFiat() {
        val days = listOf(incomeDay(0, 10.0), incomeDay(1, 5.0))
        val rates = dailyRates(listOf(usdRate(0, "0.20")))

        val week = valueIncome(listOf(incomeBucket(fromDay = 0, toDay = 7, value = 15.0)), days, rates).single()

        assertNull(week.fiat)
    }

    @Test
    fun valueIncome_zeroIncomeDayWithoutRate_doesNotBlankBucket() {
        val days = listOf(incomeDay(0, 10.0), incomeDay(1, 0.0))
        val rates = dailyRates(listOf(usdRate(0, "0.20")))

        val week = valueIncome(listOf(incomeBucket(fromDay = 0, toDay = 7, value = 10.0)), days, rates).single()

        assertAmount("2", week.fiat)
    }

    @Test
    fun valueIncome_dayOlderThanRateHistory_noFiat() {
        val days = listOf(incomeDay(0, 10.0), incomeDay(400, 10.0))
        val rates = dailyRates(listOf(usdRate(400, "0.20")))

        val fiat = valueIncome(days, days, rates).map { it.fiat }

        assertNull(fiat[0])
        assertAmount("2", fiat[1])
    }

    @Test
    fun valueIncome_dailySeriesUnavailable_noFiatInsteadOfZero() {
        val rates = dailyRates(listOf(usdRate(0, "0.20")))

        val week = valueIncome(listOf(incomeBucket(fromDay = 0, toDay = 7, value = 70.0)), null, rates).single()

        assertNull(week.fiat)
    }

    @Test
    fun valueIncome_bucketWithoutDailyIncome_noFiat() {
        val days = listOf(incomeDay(7, 10.0))
        val rates = dailyRates(listOf(usdRate(0, "0.20"), usdRate(7, "0.20")))

        val week = valueIncome(listOf(incomeBucket(fromDay = 0, toDay = 7, value = 70.0)), days, rates).single()

        assertNull(week.fiat)
    }

    @Test
    fun valueIncome_dailyIncomeShortOfBucket_noFiat() {
        val days = listOf(incomeDay(0, 10.0))
        val rates = dailyRates(listOf(usdRate(0, "0.20")))

        val week = valueIncome(listOf(incomeBucket(fromDay = 0, toDay = 7, value = 15.0)), days, rates).single()

        assertNull(week.fiat)
    }

    @Test
    fun valueIncome_tinyBucketWithoutDailyIncome_noFiat() {
        val rates = dailyRates(listOf(usdRate(0, "0.20")))

        val bucket = incomeBucket(fromDay = 0, toDay = 7, value = 0.000001)

        val week = valueIncome(listOf(bucket), emptyList(), rates).single()

        assertNull(week.fiat)
    }

    @Test
    fun valueIncome_zeroBucketWithoutDailyIncome_fiatZero() {
        val bucket = incomeBucket(fromDay = 0, toDay = 7, value = 0.0)

        val week = valueIncome(listOf(bucket), emptyList(), emptyMap()).single()

        assertAmount("0", week.fiat)
    }

    @Test
    fun dailyRates_severalPointsInDay_earliestWins() {
        val rates = dailyRates(listOf(usdRate(3, "0.50", hourOffset = 6), usdRate(3, "0.25")))

        assertEquals(BigDecimal("0.25"), rates[3L])
    }

    @Test
    fun toChartPoints_fiatMode_dropsPointsWithoutFiat() {
        val points = listOf(income(0, "10", "2"), income(1, "10", null))

        val chart = points.toChartPoints(StakingChartDisplayMode.FIAT)

        assertEquals(listOf(2f), chart.map { it.value })
    }

    @Test
    fun toChartPoints_bothMode_putsFiatIntoSecondLine() {
        val points = listOf(income(0, "10", "2"), income(1, "10", null))

        val chart = points.toChartPoints(StakingChartDisplayMode.BOTH)

        assertEquals(listOf(10f, 10f), chart.map { it.value })
        assertEquals(listOf(2f, null), chart.map { it.dominance })
    }

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(actual)))
    }

    private fun income(day: Long, coins: String, fiat: String?) =
        StakingIncomePoint(day * DAY_MS / 1000, BigDecimal(coins), fiat?.let(::BigDecimal))
}

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

internal fun incomeDay(day: Long, value: Double) = incomeBucket(day, day + 1, value)

internal fun incomeBucket(fromDay: Long, toDay: Long, value: Double) = PricePoint(
    value = value,
    balance = 0.0,
    from = fromDay * DAY_MS,
    to = toDay * DAY_MS,
    price = emptyMap(),
    balancePrice = emptyMap(),
)

internal fun usdRate(day: Long, price: String, hourOffset: Long = 0) =
    CoinPriceChart(timestamp = day * DAY_MS + hourOffset * HOUR_MS, price = BigDecimal(price))
