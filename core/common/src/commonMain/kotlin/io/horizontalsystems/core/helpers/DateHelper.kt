package io.horizontalsystems.core.helpers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.auth_info_days_ago
import cash.p.terminal.resources.auth_info_hours_ago
import cash.p.terminal.resources.auth_info_minutes_ago
import cash.p.terminal.resources.auth_info_seconds_ago
import io.horizontalsystems.core.Translator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateHelper {

    private val timeFormat: String by lazy { if (is24HourFormat()) "HH:mm" else "h:mm a" }

    fun getDayAndTime(date: Date): String = formatDate(date, "MMM d, $timeFormat")
    fun getOnlyTime(date: Date): String = formatDate(date, timeFormat)
    fun getOnlyTimeWithSec(date: Date): String = formatDate(date, "HH:mm:ss")
    fun getFullDate(date: Date): String = formatDate(date, "MMM d, yyyy, $timeFormat")

    fun shortDate(date: Date, near: String = "MMM d", far: String = "MMM dd, yyyy"): String =
        if (isThisYear(date)) {
            formatDate(date, near)
        } else {
            formatDate(date, far)
        }

    fun formatDate(date: Date, pattern: String): String {
        return SimpleDateFormat(
            bestDateTimePattern(Locale.getDefault(), pattern),
            Locale.getDefault()
        ).format(date)
    }

    fun getSecondsAgo(dateInMillis: Long): Long {
        val differenceInMillis = Date().time - dateInMillis
        return differenceInMillis / 1000
    }

    fun isSameDay(date1: Date, date2: Date): Boolean {
        val calendar1 = Calendar.getInstance().apply { time = date1 }
        val calendar2 = Calendar.getInstance().apply { time = date2 }

        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) && calendar1.get(
            Calendar.DAY_OF_YEAR
        ) == calendar2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isThisYear(date: Date): Boolean {
        val calendar1 = Calendar.getInstance()
        val calendar2 = Calendar.getInstance().apply { time = date }

        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR)
    }

    fun formatRelativeTime(timestamp: Long): String {
        val secondsAgo = (System.currentTimeMillis() - timestamp) / 1000
        return when {
            secondsAgo < 60 -> Translator.getString(Res.string.auth_info_seconds_ago, secondsAgo.toInt())
            secondsAgo < 3600 -> Translator.getString(Res.string.auth_info_minutes_ago, (secondsAgo / 60).toInt())
            secondsAgo < 86400 -> Translator.getString(Res.string.auth_info_hours_ago, (secondsAgo / 3600).toInt())
            else -> Translator.getString(Res.string.auth_info_days_ago, (secondsAgo / 86400).toInt())
        }
    }
}

internal expect fun is24HourFormat(): Boolean

internal expect fun bestDateTimePattern(locale: Locale, skeleton: String): String
