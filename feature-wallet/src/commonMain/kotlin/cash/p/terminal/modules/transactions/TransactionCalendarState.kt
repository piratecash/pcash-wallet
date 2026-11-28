package cash.p.terminal.modules.transactions

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.time.temporal.WeekFields

enum class CalendarMode { ToDate, Period }

enum class PeriodField { From, To }

enum class RangeBand { None, Full, StartHalf, EndHalf }

data class CalendarCell(
    val date: LocalDate,
    val enabled: Boolean,
    val isToday: Boolean,
    val selected: Boolean,
    val band: RangeBand,
)

/** Calendar sheet model: [CalendarMode.ToDate] selects [date], [CalendarMode.Period] selects [from]..[to]. */
data class TransactionCalendarState(
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    val mode: CalendarMode = CalendarMode.ToDate,
    val month: YearMonth = YearMonth.from(today),
    val date: LocalDate = today,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val editing: PeriodField = PeriodField.To,
    // After a finished range the next tap starts a new one; tapping a field edits that bound instead.
    private val restartOnTap: Boolean = false,
) {
    val canGoToNextMonth: Boolean get() = month < YearMonth.from(today)

    val canApply: Boolean
        get() = when (mode) {
            CalendarMode.ToDate -> true
            CalendarMode.Period -> from != null
        }

    val weekdays: List<DayOfWeek> get() = List(DAYS_IN_WEEK) { firstDayOfWeek.plus(it.toLong()) }

    fun period(): TransactionPeriod? = when (mode) {
        CalendarMode.ToDate -> TransactionPeriod(from = null, to = date)
        CalendarMode.Period -> from?.let { TransactionPeriod(from = it, to = to ?: it) }
    }

    fun selectMode(newMode: CalendarMode): TransactionCalendarState =
        copy(
            mode = newMode,
            editing = if (from == null) PeriodField.From else PeriodField.To,
            restartOnTap = false,
        )

    fun selectField(field: PeriodField): TransactionCalendarState =
        copy(editing = field, restartOnTap = false)

    fun showPreviousMonth(): TransactionCalendarState = copy(month = month.minusMonths(1))

    fun showNextMonth(): TransactionCalendarState =
        if (canGoToNextMonth) copy(month = month.plusMonths(1)) else this

    fun onDayTap(day: LocalDate): TransactionCalendarState = when {
        day.isAfter(today) -> this
        mode == CalendarMode.ToDate -> copy(date = day)
        else -> tapInPeriod(day)
    }

    private fun tapInPeriod(day: LocalDate): TransactionCalendarState = when {
        restartOnTap ->
            copy(from = day, to = null, editing = PeriodField.To, restartOnTap = false)

        editing == PeriodField.From -> {
            val end = to
            if (end != null && day.isAfter(end)) copy(from = end, to = day, editing = PeriodField.To)
            else copy(from = day, editing = PeriodField.To)
        }

        else -> {
            val start = from ?: day
            val range = if (day.isBefore(start)) day to start else start to day
            copy(from = range.first, to = range.second, editing = PeriodField.To, restartOnTap = true)
        }
    }

    /** Leading nulls pad the first week up to [firstDayOfWeek]. */
    fun cells(): List<CalendarCell?> {
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
        return List(leading) { null } + (1..month.lengthOfMonth()).map { cell(month.atDay(it)) }
    }

    private fun cell(day: LocalDate) = CalendarCell(
        date = day,
        enabled = !day.isAfter(today),
        isToday = day == today,
        selected = isSelected(day),
        band = if (mode == CalendarMode.Period) bandOf(day) else RangeBand.None,
    )

    private fun isSelected(day: LocalDate) = when (mode) {
        CalendarMode.ToDate -> day == date
        CalendarMode.Period -> day == from || day == to
    }

    private fun bandOf(day: LocalDate): RangeBand {
        val start = from
        val end = to
        if (start == null || end == null || start == end) return RangeBand.None
        return when {
            day == start -> RangeBand.StartHalf
            day == end -> RangeBand.EndHalf
            day.isAfter(start) && day.isBefore(end) -> RangeBand.Full
            else -> RangeBand.None
        }
    }

    companion object {
        private const val DAYS_IN_WEEK = 7

        fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

        /** Opens on the applied [period]: a date for an open-ended one, a range otherwise. */
        fun create(
            today: LocalDate,
            firstDayOfWeek: DayOfWeek,
            period: TransactionPeriod? = null,
        ): TransactionCalendarState {
            val base = TransactionCalendarState(today = today, firstDayOfWeek = firstDayOfWeek)
            val applied = period ?: return base
            return base.copy(
                mode = if (applied.from == null) CalendarMode.ToDate else CalendarMode.Period,
                month = YearMonth.from(applied.to),
                date = applied.to,
                from = applied.from,
                to = applied.to.takeIf { applied.from != null },
                restartOnTap = applied.from != null,
            )
        }
    }
}
