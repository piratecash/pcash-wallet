package io.horizontalsystems.core.helpers

import android.text.format.DateFormat
import io.horizontalsystems.core.ApplicationHolder
import java.util.Locale

internal actual fun is24HourFormat(): Boolean = DateFormat.is24HourFormat(ApplicationHolder.application)

internal actual fun bestDateTimePattern(locale: Locale, skeleton: String): String =
    DateFormat.getBestDateTimePattern(locale, skeleton)
