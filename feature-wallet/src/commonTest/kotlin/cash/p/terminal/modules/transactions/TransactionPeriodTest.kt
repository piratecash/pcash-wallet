package cash.p.terminal.modules.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TransactionPeriodTest {
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun timestampRange_springForwardDay_lasts23Hours() {
        val day = LocalDate.of(2024, 3, 10)

        val range = TransactionPeriod(day, day).timestampRange(newYork)

        assertEquals(startOf(day), range.since)
        assertEquals(23 * HOUR, requireNotNull(range.until) - requireNotNull(range.since))
    }

    @Test
    fun timestampRange_fallBackDay_lasts25Hours() {
        val day = LocalDate.of(2024, 11, 3)

        val range = TransactionPeriod(day, day).timestampRange(newYork)

        assertEquals(25 * HOUR, requireNotNull(range.until) - requireNotNull(range.since))
    }

    @Test
    fun timestampRange_severalDays_endsAtStartOfDayAfterTo() {
        val range = TransactionPeriod(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 3)).timestampRange(newYork)

        assertEquals(startOf(LocalDate.of(2024, 5, 1)), range.since)
        assertEquals(startOf(LocalDate.of(2024, 5, 4)), range.until)
    }

    @Test
    fun timestampRange_openFrom_hasNoLowerBound() {
        val range = TransactionPeriod(null, LocalDate.of(2024, 5, 1)).timestampRange(newYork)

        assertNull(range.since)
        assertEquals(startOf(LocalDate.of(2024, 5, 2)), range.until)
    }

    @Test
    fun contains_bounds_includesSinceAndExcludesUntil() {
        val range = TimestampRange(since = 100, until = 200)

        assertFalse(99L in range)
        assertTrue(100L in range)
        assertTrue(199L in range)
        assertFalse(200L in range)
    }

    @Test
    fun contains_openBounds_acceptsEverythingOnThatSide() {
        assertTrue(1L in TimestampRange(since = null, until = 200))
        assertTrue(Long.MAX_VALUE in TimestampRange(since = 100, until = null))
    }

    @Test
    fun contains_undatedTimestamp_matchesNoPeriod() {
        assertFalse(0L in TimestampRange(since = null, until = 200))
        assertFalse(0L in TimestampRange(since = null, until = null))
    }

    @Test
    fun oldestDatedTimestamp_undatedAndDatedRows_returnsOldestDated() {
        assertEquals(150L, listOf(300L, 0L, 150L).oldestDatedTimestamp { it })
        assertNull(listOf(0L).oldestDatedTimestamp { it })
    }

    @Test
    fun filterInPeriod_nullPeriod_keepsAllAndRangeKeepsOnlyInside() {
        val timestamps = listOf(50L, 100L, 150L, 200L)

        assertEquals(timestamps, timestamps.filterInPeriod(null) { it })
        assertEquals(listOf(100L, 150L), timestamps.filterInPeriod(TimestampRange(100, 200)) { it })
    }

    private fun startOf(day: LocalDate): Long = ZonedDateTime.of(day.atStartOfDay(), newYork).toEpochSecond()

    private companion object {
        const val HOUR = 3600L
    }
}
