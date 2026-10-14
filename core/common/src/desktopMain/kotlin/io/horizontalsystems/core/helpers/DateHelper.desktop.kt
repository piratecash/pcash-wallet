package io.horizontalsystems.core.helpers

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

internal actual fun is24HourFormat(): Boolean {
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern()
    return pattern?.contains('a') != true
}

// java.text has no skeleton API: the skeleton is already a valid pattern, SimpleDateFormat localizes names.
internal actual fun bestDateTimePattern(locale: Locale, skeleton: String): String = skeleton
