package io.horizontalsystems.core.helpers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DateHelperTest {

    private val defaultLocale = Locale.getDefault()
    private val defaultTimeZone = TimeZone.getDefault()

    private val date: Date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2020, Calendar.JANUARY, 5, 14, 7, 9)
    }.time

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun getOnlyTime_usLocale_uses12HourClock() {
        assertEquals("2:07 PM", DateHelper.getOnlyTime(date))
    }

    @Test
    fun getDayAndTime_usLocale_monthDayAndTime() {
        assertEquals("Jan 5, 2:07 PM", DateHelper.getDayAndTime(date))
    }

    @Test
    fun getFullDate_usLocale_includesYear() {
        assertEquals("Jan 5, 2020, 2:07 PM", DateHelper.getFullDate(date))
    }

    @Test
    fun getOnlyTimeWithSec_usLocale_uses24HourClock() {
        assertEquals("14:07:09", DateHelper.getOnlyTimeWithSec(date))
    }

    @Test
    fun shortDate_otherYear_usesFarPattern() {
        assertEquals("Jan 05, 2020", DateHelper.shortDate(date))
    }

    @Test
    fun shortDate_thisYear_usesNearPattern() {
        val thisYear = Calendar.getInstance().apply { set(Calendar.MONTH, Calendar.MARCH); set(Calendar.DAY_OF_MONTH, 9) }.time

        assertEquals("Mar 9", DateHelper.shortDate(thisYear))
    }

    @Test
    fun formatDate_customPattern_isLocalized() {
        assertEquals("Sunday", DateHelper.formatDate(date, "EEEE"))
    }

    @Test
    fun isSameDay_sameCalendarDay_true() {
        assertTrue(DateHelper.isSameDay(date, Date(date.time + 60_000)))
    }

    @Test
    fun isSameDay_nextDay_false() {
        assertFalse(DateHelper.isSameDay(date, Date(date.time + 24 * 3_600_000L)))
    }

    @Test
    fun getSecondsAgo_pastTimestamp_returnsWholeSeconds() {
        assertEquals(42L, DateHelper.getSecondsAgo(System.currentTimeMillis() - 42_500))
    }

    @Test
    fun formatRelativeTime_secondsAgo_usesSharedStringResource() {
        assertEquals("(30 sec ago)", DateHelper.formatRelativeTime(System.currentTimeMillis() - 30_500))
    }
}
