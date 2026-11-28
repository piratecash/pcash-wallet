package io.horizontalsystems.core.entities

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.login_logging_periods
import cash.p.terminal.resources.login_logging_periods_short
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle

enum class AutoDeletePeriod(val value: Int) : WithTranslatableTitle {
    NEVER(0),
    MONTH(1),
    YEAR(2);

    override val title: TranslatableString
        get() = TranslatableString.ResArrayString(Res.array.login_logging_periods, ordinal)

    val shortTitle: TranslatableString
        get() = TranslatableString.ResArrayString(Res.array.login_logging_periods_short, ordinal)

    /** Records older than this go; null keeps them all. */
    fun cutoff(now: Long): Long? = when (this) {
        NEVER -> null
        MONTH -> now - 30L * DAY_MILLIS
        YEAR -> now - 365L * DAY_MILLIS
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        fun fromValue(value: Int): AutoDeletePeriod =
            entries.find { it.value == value } ?: YEAR
    }
}
