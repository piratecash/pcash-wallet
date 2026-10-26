package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.NonFatalReporter
import cash.p.terminal.core.currentProcessAgeMillis
import cash.p.terminal.core.currentProcessId
import cash.p.terminal.core.tryOrNull
import cash.p.zcash.DatabaseStats
import cash.p.zcash.ZcashSdk
import io.horizontalsystems.core.logger.AppLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class ZcashCantOpenException(message: String) : Exception(message)

/** Process-wide, like the kit counters it watches: one instance reacts to database failures of every account. */
class ZcashDatabaseFailureReporter(
    private val databaseFiles: ZcashDatabaseFiles,
    private val nonFatalReporter: NonFatalReporter,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private class Counters(val native: Long, val acquire: Long, val recovered: Long, val seq: Long)

    private val seen = AtomicReference(Counters(0, 0, 0, 0))
    private val reportedAt = ConcurrentHashMap<String, TimeMark>()
    private val processStartLogged = AtomicBoolean(false)

    fun databaseFailure(accountId: String, logger: AppLogger) {
        logger.warning(databaseFiles.diagnostics(accountId))
        poolsLine()?.let(logger::warning)
    }

    /** Logs the healthy baseline once per process, to compare against the first code-14 line. */
    fun processStarted(logger: AppLogger) {
        if (processStartLogged.compareAndSet(false, true)) logger.info(processLine())
    }

    private fun poolsLine(): String? {
        val pools = tryOrNull { ZcashSdk.databaseStats() }?.pools.orEmpty()
        if (pools.isEmpty()) return null
        return pools.joinToString("; ", prefix = "db-pools: ") {
            "${databaseFiles.accountIdOf(it.path) ?: "other"} pool=${it.id} size=${it.size} idle=${it.idle}"
        }
    }

    /** Logs and reports SQLite code-14 activity the kit counted since the previous call. */
    fun checkCantOpen(logger: AppLogger) {
        val stats = tryOrNull { ZcashSdk.databaseStats() } ?: return
        val previous = seen.getAndUpdate {
            val grew = stats.nativeCantOpen > it.native || stats.cantOpenFailures > it.acquire ||
                stats.recoveredByRetry > it.recovered
            Counters(
                maxOf(it.native, stats.nativeCantOpen),
                maxOf(it.acquire, stats.cantOpenFailures),
                maxOf(it.recovered, stats.recoveredByRetry),
                if (grew) maxOf(it.seq, stats.lastSeq) else it.seq,
            )
        }
        val native = stats.nativeCantOpen - previous.native
        val acquire = stats.cantOpenFailures - previous.acquire
        val recovered = stats.recoveredByRetry - previous.recovered
        if (native <= 0 && acquire <= 0 && recovered <= 0) return
        val summary = "sqlite cantopen native +$native acquire +$acquire recovered +$recovered opens=${stats.opens}"
        logger.warning(summary)
        logUnseenEntries(stats, previous.seq, logger)
        if (isDueForNonFatal(signature(stats))) {
            nonFatalReporter.report(ZcashCantOpenException(summary), summary)
        }
    }

    private fun logUnseenEntries(stats: DatabaseStats, previousSeq: Long, logger: AppLogger) {
        val unseen = (stats.lastSeq - previousSeq).coerceAtLeast(0)
        val shown = minOf(unseen, stats.recent.size.toLong()).toInt()
        if (unseen > shown) logger.warning("sqlite log: ${unseen - shown} entries evicted unseen")
        stats.recent.takeLast(shown).forEach(logger::warning)
    }

    private fun isDueForNonFatal(signature: String): Boolean {
        var due = false
        reportedAt.compute(signature) { _, mark ->
            if (mark == null || mark.elapsedNow() >= NON_FATAL_WINDOW) {
                due = true
                timeSource.markNow()
            } else {
                mark
            }
        }
        return due
    }

    /** The latest os-layer line (`<syscall>(<path>) - <errno text>`), digits removed so line numbers and errno codes do not split it. */
    private fun signature(stats: DatabaseStats): String =
        stats.recent.asReversed().firstNotNullOfOrNull(::osLayerText)?.filterNot(Char::isDigit) ?: UNKNOWN

    private fun osLayerText(entry: String): String? {
        val (code, text) = entry.split(": ", limit = 2).takeIf { it.size == 2 } ?: return null
        val isOsLayer = text.contains(") - ") &&
            !text.startsWith(KIT_ENTRY_PREFIX) &&
            !text.contains(BREAKPOINT_MARKER)
        return text.takeIf { isOsLayer && code.toIntOrNull()?.and(PRIMARY_CODE_MASK) == CANTOPEN }
    }

    private fun processLine(): String {
        val ageSeconds = tryOrNull { currentProcessAgeMillis() / 1000 }
        return "process: start pid=${tryOrNull { currentProcessId() } ?: NA} age=${ageSeconds ?: NA}s"
    }

    private companion object {
        const val NA = "n/a"
        val NON_FATAL_WINDOW = 6.hours
        const val UNKNOWN = "unknown"
        const val KIT_ENTRY_PREFIX = "acquire "
        const val BREAKPOINT_MARKER = "cannot open file at line"
        const val CANTOPEN = 14
        const val PRIMARY_CODE_MASK = 0xff
    }
}
