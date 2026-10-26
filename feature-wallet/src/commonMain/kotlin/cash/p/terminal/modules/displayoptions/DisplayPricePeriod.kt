package cash.p.terminal.modules.displayoptions

import cash.p.terminal.resources.CoinPage_TimeDuration_All
import cash.p.terminal.resources.CoinPage_TimeDuration_Day
import cash.p.terminal.resources.CoinPage_TimeDuration_Hour
import cash.p.terminal.resources.CoinPage_TimeDuration_Month
import cash.p.terminal.resources.CoinPage_TimeDuration_Week
import cash.p.terminal.resources.CoinPage_TimeDuration_Year
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.display_options_period_1d
import cash.p.terminal.resources.display_options_period_1h
import cash.p.terminal.resources.display_options_period_1m
import cash.p.terminal.resources.display_options_period_1w
import cash.p.terminal.resources.display_options_period_1y
import cash.p.terminal.resources.display_options_period_all
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle

enum class DisplayPricePeriod(val key: String): WithTranslatableTitle {
    ONE_HOUR("1h"),
    ONE_DAY("1d"),
    ONE_WEEK("1w"),
    ONE_MONTH("1m"),
    ONE_YEAR("1y"),
    ALL("all");

    override val title: TranslatableString
        get() = TranslatableString.ResString(when(this) {
            ONE_HOUR -> Res.string.display_options_period_1h
            ONE_DAY -> Res.string.display_options_period_1d
            ONE_WEEK -> Res.string.display_options_period_1w
            ONE_MONTH -> Res.string.display_options_period_1m
            ONE_YEAR -> Res.string.display_options_period_1y
            ALL -> Res.string.display_options_period_all
        })

    val shortForm: TranslatableString
        get() = TranslatableString.ResString(when(this) {
            ONE_HOUR -> Res.string.CoinPage_TimeDuration_Hour
            ONE_DAY -> Res.string.CoinPage_TimeDuration_Day
            ONE_WEEK -> Res.string.CoinPage_TimeDuration_Week
            ONE_MONTH -> Res.string.CoinPage_TimeDuration_Month
            ONE_YEAR -> Res.string.CoinPage_TimeDuration_Year
            ALL -> Res.string.CoinPage_TimeDuration_All
        })
}
