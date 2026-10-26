package cash.p.terminal.core.utils

import cash.p.terminal.entities.SwapDuration
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.duration_short_hours
import cash.p.terminal.resources.duration_short_minutes
import cash.p.terminal.resources.duration_short_seconds
import cash.p.terminal.resources.swap_duration_approximate
import io.horizontalsystems.core.Translator
import kotlin.math.roundToLong

fun formatDurationShort(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val parts = mutableListOf<String>()
    if (hours > 0) parts += Translator.getString(Res.string.duration_short_hours, hours)
    if (minutes > 0) parts += Translator.getString(Res.string.duration_short_minutes, minutes)
    if (seconds > 0 || (hours == 0L && minutes == 0L)) {
        parts += Translator.getString(Res.string.duration_short_seconds, seconds)
    }
    return parts.joinToString(" ")
}

fun formatSwapDuration(duration: SwapDuration): String {
    val text = formatDurationShort((duration.millis / 1000.0).roundToLong())
    return if (duration.approximate) Translator.getString(Res.string.swap_duration_approximate, text) else text
}
