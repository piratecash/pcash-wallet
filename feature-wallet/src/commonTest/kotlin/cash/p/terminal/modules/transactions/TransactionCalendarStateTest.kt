package cash.p.terminal.modules.transactions

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionCalendarStateTest {
    private val today = LocalDate.of(2026, 11, 23)

    private fun state(firstDay: DayOfWeek = DayOfWeek.MONDAY) =
        TransactionCalendarState.create(today, firstDay)

    private fun periodState() = state().selectMode(CalendarMode.Period)

    private fun day(d: Int, month: Int = 11) = LocalDate.of(2026, month, d)

    @Test
    fun firstDayOfWeek_russianLocale_isMonday() {
        assertEquals(DayOfWeek.MONDAY, TransactionCalendarState.firstDayOfWeek(Locale.forLanguageTag("ru-RU")))
    }

    @Test
    fun firstDayOfWeek_usLocale_isSunday() {
        assertEquals(DayOfWeek.SUNDAY, TransactionCalendarState.firstDayOfWeek(Locale.US))
    }

    @Test
    fun cells_mondayStart_padsLeadingEmptyCells() {
        // 1 Nov 2026 is a Sunday: six empty cells before it when the week starts on Monday
        val cells = state(DayOfWeek.MONDAY).cells()

        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(6 + 30, cells.size)
    }

    @Test
    fun cells_sundayStart_hasNoLeadingCells() {
        val cells = state(DayOfWeek.SUNDAY).cells()

        assertEquals(day(1), cells.first()?.date)
        assertEquals(30, cells.size)
    }

    @Test
    fun weekdays_startFromFirstDayOfWeek() {
        assertEquals(DayOfWeek.WEDNESDAY, state(DayOfWeek.WEDNESDAY).weekdays.first())
        assertEquals(DayOfWeek.TUESDAY, state(DayOfWeek.WEDNESDAY).weekdays.last())
    }

    @Test
    fun cells_currentMonth_disablesFutureAndMarksToday() {
        val cells = state().cells().filterNotNull()

        assertTrue(cells.first { it.date == day(23) }.enabled)
        assertTrue(cells.first { it.date == day(23) }.isToday)
        assertFalse(cells.first { it.date == day(24) }.enabled)
        assertEquals(1, cells.count { it.isToday })
    }

    @Test
    fun showNextMonth_onCurrentMonth_staysAndIsDisabled() {
        val s = state()

        assertFalse(s.canGoToNextMonth)
        assertEquals(s, s.showNextMonth())
    }

    @Test
    fun showNextMonth_afterPrevious_returnsToCurrentMonth() {
        val s = state().showPreviousMonth()

        assertTrue(s.canGoToNextMonth)
        assertEquals(YearMonth.of(2026, 11), s.showNextMonth().month)
    }

    @Test
    fun onDayTap_toDateMode_selectsSingleDay() {
        val s = state().onDayTap(day(12))

        assertEquals(TransactionPeriod(null, day(12)), s.period())
        assertEquals(listOf(day(12)), s.cells().filterNotNull().filter { it.selected }.map { it.date })
    }

    @Test
    fun onDayTap_futureDay_isIgnored() {
        val s = state()

        assertEquals(s, s.onDayTap(day(24)))
    }

    @Test
    fun period_toDateDefault_isTodayWithOpenStart() {
        assertEquals(TransactionPeriod(null, today), state().period())
    }

    @Test
    fun selectMode_backToDate_keepsChosenDate() {
        val s = state().onDayTap(day(10)).selectMode(CalendarMode.Period).onDayTap(day(15)).selectMode(CalendarMode.ToDate)

        assertEquals(TransactionPeriod(null, day(10)), s.period())
    }

    @Test
    fun onDayTap_periodModeTwoTaps_buildsRangeAndMovesToEndField() {
        val s = periodState().onDayTap(day(3)).onDayTap(day(9))

        assertEquals(TransactionPeriod(day(3), day(9)), s.period())
        assertEquals(PeriodField.To, s.editing)
    }

    @Test
    fun onDayTap_periodModeSecondTapEarlier_swapsBounds() {
        val s = periodState().onDayTap(day(9)).onDayTap(day(3))

        assertEquals(TransactionPeriod(day(3), day(9)), s.period())
    }

    @Test
    fun onDayTap_periodModeAfterRangeComplete_startsNewRange() {
        val s = periodState().onDayTap(day(3)).onDayTap(day(9)).onDayTap(day(15))

        assertEquals(day(15), s.from)
        assertNull(s.to)
        assertEquals(TransactionPeriod(day(15), day(15)), s.period())
    }

    @Test
    fun onDayTap_periodModeSingleTap_appliesAsSingleDay() {
        val s = periodState().onDayTap(day(5))

        assertTrue(s.canApply)
        assertEquals(TransactionPeriod(day(5), day(5)), s.period())
    }

    @Test
    fun onDayTap_editingFromFieldAfterRange_changesOnlyStart() {
        val s = periodState().onDayTap(day(3)).onDayTap(day(9))
            .selectField(PeriodField.From).onDayTap(day(1))

        assertEquals(TransactionPeriod(day(1), day(9)), s.period())
        assertEquals(PeriodField.To, s.editing)
    }

    @Test
    fun onDayTap_editingFromFieldAfterEnd_swapsBounds() {
        val s = periodState().onDayTap(day(3)).onDayTap(day(9))
            .selectField(PeriodField.From).onDayTap(day(20))

        assertEquals(TransactionPeriod(day(9), day(20)), s.period())
    }

    @Test
    fun onDayTap_editingToFieldAfterRange_changesOnlyEnd() {
        val s = periodState().onDayTap(day(3)).onDayTap(day(9))
            .selectField(PeriodField.To).onDayTap(day(12))

        assertEquals(TransactionPeriod(day(3), day(12)), s.period())
    }

    @Test
    fun cells_range_assignsStartMiddleEndRoles() {
        val cells = periodState().onDayTap(day(3)).onDayTap(day(5)).cells().filterNotNull().associateBy { it.date }

        assertEquals(RangeBand.StartHalf, cells.getValue(day(3)).band)
        assertEquals(RangeBand.Full, cells.getValue(day(4)).band)
        assertEquals(RangeBand.EndHalf, cells.getValue(day(5)).band)
        assertEquals(RangeBand.None, cells.getValue(day(6)).band)
        assertTrue(cells.getValue(day(3)).selected && cells.getValue(day(5)).selected)
        assertFalse(cells.getValue(day(4)).selected)
    }

    @Test
    fun cells_singleDayRange_hasSelectedCellWithoutBand() {
        val cell = periodState().onDayTap(day(3)).onDayTap(day(3)).cells().filterNotNull().first { it.date == day(3) }

        assertTrue(cell.selected)
        assertEquals(RangeBand.None, cell.band)
    }

    @Test
    fun selectMode_toPeriodWithoutStart_editsStartField() {
        assertEquals(PeriodField.From, periodState().editing)
    }

    @Test
    fun create_withAppliedRange_opensPeriodModeOnEndMonth() {
        val s = TransactionCalendarState.create(
            today, DayOfWeek.MONDAY, TransactionPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 28)),
        )

        assertEquals(CalendarMode.Period, s.mode)
        assertEquals(YearMonth.of(2026, 8), s.month)
        assertEquals(LocalDate.of(2026, 9, 5), s.onDayTap(LocalDate.of(2026, 9, 5)).from)
    }

    @Test
    fun create_withAppliedOpenStart_opensToDateMode() {
        val s = TransactionCalendarState.create(today, DayOfWeek.MONDAY, TransactionPeriod(null, day(12)))

        assertEquals(CalendarMode.ToDate, s.mode)
        assertEquals(TransactionPeriod(null, day(12)), s.period())
    }
}
