package cash.p.terminal.core.adapters.zcash.server

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.adapters.zcash.isZcashNetworkFailure
import cash.p.terminal.core.adapters.zcash.session.ZcashPassOutcome
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.core.managers.sanitizeDiagnosticText
import cash.p.terminal.core.managers.sanitizeNetworkUrl
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.zcash.ServerProbe
import cash.p.zcash.Transport
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

private const val MINUTE_MS = 60_000L
private const val EVALUATION_INTERVAL_MS = 30 * MINUTE_MS
private const val BREAK_WINDOW_MS = 60 * MINUTE_MS
private val QUARANTINE_MINUTES = listOf(10L, 20L, 40L, 60L)
private const val RANGE_BLOCKS = 10
private const val LATENCY_SAMPLES = 3
private const val BREAK_PENALTY_MS = 1_000L
private const val HYSTERESIS = 0.7
private const val MAIN_CHAIN = "main"
private const val SAPLING_ACTIVATION_HEIGHT = 419_200
private const val MAX_CATCH_UP_BLOCKS = 2
private const val MAX_LAG_BLOCKS = 3

/**
 * Picks the automatic Zcash server: probes the pool, admits only servers on the right chain and
 * near the tip, and moves away from a server only when another is clearly better or it keeps failing.
 */
class ZcashServerSelector(
    private val serverManager: ZcashServerManager,
    private val prober: ZcashServerProber,
    private val connectivityManager: ConnectivityManager,
    private val localStorage: ILocalStorage,
    private val backgroundManager: BackgroundManager,
    private val uptimeProvider: UptimeProvider,
    dispatcherProvider: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val mutex = Mutex()
    private val records = ZcashServerRecords()

    @Volatile
    private var reason = "not evaluated"

    @Volatile
    private var lastEvaluationUptime: Long? = null

    @Volatile
    private var reevaluationRequested = false

    private var choseOnce = false

    init {
        scope.launch { connectivityManager.validatedNetworksFlow.drop(1).collect { reevaluationRequested = true } }
        // Not serverSelectedFlow: our own applyAutoChoice emits there.
        scope.launch { serverManager.serversUpdatedFlow.collect { reevaluationRequested = true } }
    }

    /** Non-blocking; called at the start of every scheduler tick. */
    fun evaluateIfDue() {
        if (!canProbe()) return
        val now = uptimeProvider.uptime
        val last = lastEvaluationUptime
        val cadenceDue = last != null && backgroundManager.inForeground && now - last >= EVALUATION_INTERVAL_MS
        if (last != null && !reevaluationRequested && !cadenceDue) return
        lastEvaluationUptime = now
        reevaluationRequested = false
        scope.launch { mutex.withLock { evaluate() } }
    }

    /**
     * [url] is the server the pass ran on, captured before it started. Suspends until the decision is
     * made, so the next pass cannot overtake a failure verdict.
     */
    suspend fun onPassFinished(url: String, outcome: ZcashPassOutcome, breaks: Long) = mutex.withLock {
        records.recordBreaks(url, breaks, uptimeProvider.uptime)
        when (outcome) {
            ZcashPassOutcome.Synced -> records.clearQuarantine(url)
            ZcashPassOutcome.Cancelled -> Unit
            is ZcashPassOutcome.Failed -> if (outcome.error.isZcashNetworkFailure()) onNetworkFailure(url)
        }
    }

    fun statusRows(): Map<String, String> {
        val now = uptimeProvider.uptime
        return mapOf(
            "Server selection" to if (serverManager.isAuto) "auto: $reason" else "manual",
            "Server health" to serverManager.allServers.joinToString("\n") {
                "${label(it.url)}: ${records.describe(it.url, now)}"
            },
        )
    }

    private fun canProbe() = serverManager.isAuto && connectivityManager.isConnected.value

    private suspend fun evaluate() {
        if (!canProbe()) return
        val candidates = candidates()
        if (candidates.isEmpty()) {
            reason = "all quarantined"
            return
        }
        val health = probe(candidates) ?: return
        val best = bestAdmitted(health) ?: run {
            reason = "none admitted"
            return
        }
        val currentScore = currentScore(health)
        val keep = choseOnce && currentScore != null && best.score >= HYSTERESIS * currentScore
        reason = choiceReason(keep, best.score, currentScore)
        choseOnce = true
        if (!keep) serverManager.applyAutoChoice(best.url)
    }

    /** Null when the current server cannot stay: unreachable, rejected or quarantined. */
    private fun currentScore(health: Map<String, ZcashServerHealth>): Long? {
        val url = serverManager.current.url
        return health[url]?.takeIf { it.admitted && !records.isQuarantined(url, now()) }?.let { score(url, it) }
    }

    private fun choiceReason(keep: Boolean, bestScore: Long, currentScore: Long?): String = when {
        !choseOnce -> "startup best"
        keep -> "current kept (hysteresis)"
        currentScore == null -> "current rejected"
        else -> "faster by ${((currentScore - bestScore) * 100.0 / currentScore).roundToInt()}%"
    }

    private suspend fun onNetworkFailure(url: String) {
        if (!canProbe()) return
        val health = probe((candidates() + url).distinct()) ?: return
        records.quarantine(url, now())
        if (serverManager.current.url != url) return
        val alternatives = health.filterKeys { it != url && !records.isQuarantined(it, now()) }
        val best = bestAdmitted(alternatives) ?: run {
            reason = "no alternative"
            return
        }
        serverManager.applyAutoChoice(best.url)
        reason = "quarantined ${label(url)}"
    }

    /** These rows go into shared reports, so a custom server is shown as a redacted URL. */
    private fun label(url: String): String =
        serverManager.defaultServers.firstOrNull { it.url == url }?.name ?: sanitizeNetworkUrl(url)

    private fun candidates(): List<String> =
        serverManager.autoPool(localStorage.torEnabled).map { it.url }.filterNot { records.isQuarantined(it, now()) }

    /** Null when nothing answered: that is the device's network, not the servers. */
    private suspend fun probe(urls: List<String>): Map<String, ZcashServerHealth>? {
        val torEnabled = localStorage.torEnabled
        val probes = coroutineScope {
            urls.map { url -> async { url to prober.measure(url, serverManager.transportFor(url, torEnabled)) } }
                .awaitAll()
                .toMap()
        }
        if (probes.values.all { it == null }) {
            reason = "no network"
            return null
        }
        return assess(probes).also { records.updateHealth(it) }
    }

    private fun bestAdmitted(health: Map<String, ZcashServerHealth>): ScoredServer? =
        health.filterValues { it.admitted }
            .map { (url, it) -> ScoredServer(url, score(url, it)) }
            .minByOrNull { it.score }

    private fun score(url: String, health: ZcashServerHealth): Long {
        val probe = requireNotNull(health.probe)
        return probe.infoMs + probe.rangeMs + BREAK_PENALTY_MS * records.recentBreaks(url, now())
    }

    private fun now() = uptimeProvider.uptime
}

private data class ScoredServer(val url: String, val score: Long)

private data class ZcashServerHealth(val probe: ServerProbe?, val lag: Int) {
    val rejection: String? = probe?.rejection(lag)
    val admitted: Boolean get() = probe != null && rejection == null
}

private fun ServerProbe.chainMismatch(): String? = when {
    chainName != MAIN_CHAIN -> "chain ${sanitizeDiagnosticText(chainName)}"
    saplingActivationHeight != SAPLING_ACTIVATION_HEIGHT -> "sapling $saplingActivationHeight"
    !branchIdMatches -> "branch id"
    else -> null
}

private fun ServerProbe.isCatchingUp() = estimatedHeight - blockHeight > MAX_CATCH_UP_BLOCKS

private fun ServerProbe.rejection(lag: Int): String? = chainMismatch() ?: when {
    rangeBlocksReceived < RANGE_BLOCKS -> "range $rangeBlocksReceived/$RANGE_BLOCKS"
    isCatchingUp() -> "catching up"
    lag > MAX_LAG_BLOCKS -> "lag $lag"
    else -> null
}

/**
 * Lag is measured against the median height of servers on our chain that claim to be at the tip,
 * so neither a far-ahead server nor a foreign chain rejects the rest.
 */
private fun assess(probes: Map<String, ServerProbe?>): Map<String, ZcashServerHealth> {
    val heights = probes.values.filterNotNull()
        .filter { it.chainMismatch() == null && !it.isCatchingUp() }
        .map { it.blockHeight }
        .sorted()
    val medianHeight = heights.getOrNull(heights.size / 2)
    return probes.mapValues { (_, probe) ->
        val lag = if (probe == null || medianHeight == null) 0 else (medianHeight - probe.blockHeight).coerceAtLeast(0)
        ZcashServerHealth(probe, lag)
    }
}

/** Median of three latencies; the kit needs at least one block per call, so the repeats fetch just one. */
private suspend fun ZcashServerProber.measure(url: String, transport: Transport): ServerProbe? = try {
    val first = probe(url, transport, RANGE_BLOCKS)
    val latencies = listOf(first.infoMs) + List(LATENCY_SAMPLES - 1) { probe(url, transport, 1).infoMs }
    first.copy(infoMs = latencies.sorted()[LATENCY_SAMPLES / 2])
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** Readable from any thread: every field is an immutable snapshot replaced under the selector's mutex. */
private class ZcashServerRecords {
    private data class Quarantine(val level: Int, val untilUptime: Long)

    @Volatile
    private var health = emptyMap<String, ZcashServerHealth>()

    @Volatile
    private var quarantines = emptyMap<String, Quarantine>()

    @Volatile
    private var breaks = emptyMap<String, List<Long>>()

    fun updateHealth(update: Map<String, ZcashServerHealth>) {
        health = health + update
    }

    fun recordBreaks(url: String, count: Long, now: Long) {
        val recent = breaks[url].orEmpty().filter { now - it < BREAK_WINDOW_MS }
        breaks = breaks + (url to recent + List(count.toInt()) { now })
    }

    fun recentBreaks(url: String, now: Long): Int = breaks[url].orEmpty().count { now - it < BREAK_WINDOW_MS }

    fun quarantine(url: String, now: Long) {
        val level = (quarantines[url]?.level ?: 0) + 1
        val minutes = QUARANTINE_MINUTES[minOf(level, QUARANTINE_MINUTES.size) - 1]
        quarantines = quarantines + (url to Quarantine(level, now + minutes * MINUTE_MS))
    }

    fun clearQuarantine(url: String) {
        quarantines = quarantines - url
    }

    fun isQuarantined(url: String, now: Long): Boolean = (quarantines[url]?.untilUptime ?: 0) > now

    fun describe(url: String, now: Long): String {
        val serverHealth = health[url]
        val probe = serverHealth?.probe
        val quarantineLeft = quarantines[url]?.untilUptime?.minus(now)?.takeIf { it > 0 }
        return listOfNotNull(
            when {
                serverHealth == null -> "not probed"
                probe == null -> "unreachable"
                else -> "info ${probe.infoMs}ms, range ${probe.rangeMs}ms, lag ${serverHealth.lag}"
            },
            "breaks/1h ${recentBreaks(url, now)}",
            serverHealth?.rejection?.let { "rejected: $it" },
            quarantineLeft?.let { "quarantined ${(it + MINUTE_MS - 1) / MINUTE_MS}m left" },
        ).joinToString(", ")
    }
}
