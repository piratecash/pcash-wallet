package cash.p.terminal.modules.transactions

import java.time.LocalDate
import java.time.ZoneId

/** Calendar selection; a null [from] means from the start of history. */
data class TransactionPeriod(val from: LocalDate?, val to: LocalDate) {
    // Day bounds come from the zone, not +86400: a DST day lasts 23 or 25 hours.
    fun timestampRange(zone: ZoneId) = TimestampRange(
        since = from?.atStartOfDay(zone)?.toEpochSecond(),
        until = to.plusDays(1).atStartOfDay(zone).toEpochSecond(),
    )
}

/** Seconds, half-open: since <= timestamp < until; a null bound is open. Undated (0) rows match no period. */
data class TimestampRange(val since: Long?, val until: Long?) {
    operator fun contains(timestamp: Long): Boolean =
        timestamp > 0 && (since == null || timestamp >= since) && (until == null || timestamp < until)
}

fun <T> List<T>.filterInPeriod(period: TimestampRange?, timestamp: (T) -> Long): List<T> =
    if (period == null) this else filter { timestamp(it) in period }

fun <T> Iterable<T>.oldestDatedTimestamp(timestamp: (T) -> Long): Long? =
    mapNotNull { item -> timestamp(item).takeIf { it > 0 } }.minOrNull()
