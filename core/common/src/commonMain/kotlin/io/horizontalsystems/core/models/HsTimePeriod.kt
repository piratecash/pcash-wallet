package io.horizontalsystems.core.models

import cash.p.terminal.resources.CoinPage_TimeDuration_Day
import cash.p.terminal.resources.CoinPage_TimeDuration_Hour
import cash.p.terminal.resources.CoinPage_TimeDuration_Month
import cash.p.terminal.resources.CoinPage_TimeDuration_Week
import cash.p.terminal.resources.CoinPage_TimeDuration_Year
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.StringResource

enum class HsTimePeriod(val value: String) {
    Hour1("1h"),
    Day1("1d"),
    Week1("1w"),
    Month1("1m"),
    Year1("1y");

    val range: Long
        get() = when (this) {
            Hour1 -> 60 * 60
            Day1 -> day
            Week1 -> 7 * day
            Month1 -> 30 * day
            Year1 -> 365 * day
        }

    private val day = (24 * 60 * 60).toLong()
}

val HsTimePeriod.stringResId: StringResource
    get() = when (this) {
        HsTimePeriod.Hour1 -> Res.string.CoinPage_TimeDuration_Hour
        HsTimePeriod.Day1 -> Res.string.CoinPage_TimeDuration_Day
        HsTimePeriod.Week1 -> Res.string.CoinPage_TimeDuration_Week
        HsTimePeriod.Month1 -> Res.string.CoinPage_TimeDuration_Month
        HsTimePeriod.Year1 -> Res.string.CoinPage_TimeDuration_Year
    }

sealed class HsPeriodType(open val timePeriod: HsTimePeriod?) {

    data class ByPeriod(override val timePeriod: HsTimePeriod) : HsPeriodType(timePeriod)
    data class ByCustomPoints(override val timePeriod: HsTimePeriod, val pointsCount: Int) : HsPeriodType(timePeriod)
    data class ByStartTime(val startTime: Long) : HsPeriodType(null)

    val range: Long?
        get() = when (this) {
            is ByPeriod -> timePeriod.range
            is ByStartTime -> null
            is ByCustomPoints -> timePeriod.range
        }

    fun serialize() = when (this) {
        is ByPeriod -> "period:${timePeriod.value}"
        is ByStartTime -> "startTime:$startTime"
        is ByCustomPoints -> "customPoints:${timePeriod.value}:$pointsCount"
    }

    companion object {
        fun deserialize(v: String): HsPeriodType? {
            val (type, value) = v.split(":", limit = 2)

            return when (type) {
                "period" -> {
                    HsTimePeriod
                        .values()
                        .firstOrNull { it.value == value }
                        ?.let { ByPeriod(it) }
                }
                "startTime" -> ByStartTime(value.toLong())
                "customPoints" -> {
                    val (timePeriod, pointsCount) = value.split(":")

                    HsTimePeriod
                        .values()
                        .firstOrNull { it.value == timePeriod }
                        ?.let {
                            ByCustomPoints(it, pointsCount.toInt())
                        }
                }
                else -> null
            }
        }
    }


}
