package io.horizontalsystems.core

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

fun Instant.hoursUntil(now: Instant = Instant.now()): Int? {
    val seconds = Duration.between(now, this).seconds
    if (seconds <= 0) return null

    return maxOf(1, ceil(seconds / 3600.0).toInt())
}
