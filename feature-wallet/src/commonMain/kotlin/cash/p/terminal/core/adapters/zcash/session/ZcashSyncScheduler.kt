package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.adapters.zcash.ZcashTransparentIndexStore
import cash.p.terminal.core.adapters.zcash.server.ZcashServerProber
import cash.p.terminal.core.adapters.zcash.server.ZcashServerSelector
import cash.p.terminal.core.adapters.zcash.zcashErrorName
import cash.p.terminal.core.adapters.zcash.zcashLogger
import cash.p.terminal.core.adapters.zcash.zcashRestartDelayFor
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.pin.core.UptimeProvider
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Runs the open sessions one at a time. The SDK serializes syncs process-wide anyway, so the
 * queue is what decides whose turn it is instead of leaving them to fight over the lock.
 */
class ZcashSyncScheduler(
    private val offlineModeManager: OfflineModeManager,
    private val backgroundManager: IBackgroundManager,
    private val uptimeProvider: UptimeProvider,
    private val selector: ZcashServerSelector,
    private val prober: ZcashServerProber,
    private val blockDownloadPolicy: ZcashBlockDownloadPolicy,
    private val transparentIndexStore: ZcashTransparentIndexStore,
    private val addressSearch: ZcashAddressSearch,
    dispatcherProvider: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val mutex = Mutex()
    private val queue = mutableListOf<ZcashSession>()
    private var loop: Job? = null
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)

    suspend fun enqueue(session: ZcashSession) = mutex.withLock {
        if (queue.none { it === session }) queue += session
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
    }

    suspend fun remove(session: ZcashSession) {
        mutex.withLock { queue.removeAll { it === session } }
    }

    /** Cuts the current wait short, so the next turn starts at once. */
    fun wakeUp() {
        wakeUps.trySend(Unit)
    }

    private suspend fun runLoop() {
        while (true) {
            val session = mutex.withLock { rotate() } ?: return
            val pause = if (session.isNetworkPaused()) POLL_INTERVAL_MS else runTick(session)
            withTimeoutOrNull(pause) { wakeUps.receive() }
        }
    }

    private fun rotate(): ZcashSession? {
        val session = queue.removeFirstOrNull()
        if (session == null) loop = null else queue += session
        return session
    }

    private fun ZcashSession.isNetworkPaused() =
        offlineModeManager.isNetworkPaused(accountId, BlockchainType.Zcash)

    /** Returns how long to wait before the next turn. */
    private suspend fun runTick(session: ZcashSession): Long {
        selector.evaluateIfDue()
        if (!prepareTransparentIndex(session)) return POLL_INTERVAL_MS
        maybeDiscover(session)
        val searchPending = searchAddresses(session)
        // The walks may have taken long enough for offline mode to arrive in the meantime.
        if (session.isNetworkPaused()) return POLL_INTERVAL_MS
        val breaksBefore = prober.streamBreaks()
        val result = session.sync(blockDownloadPolicy.blockDownload)
        if (result !is ZcashSessionResult.Success) {
            remove(session)
            return 0
        }
        val outcome = result.value
        if (!session.isNetworkPaused()) {
            selector.onPassFinished(session.passServerUrl, outcome, prober.streamBreaks() - breaksBefore)
        }
        return when (outcome) {
            ZcashPassOutcome.Synced -> {
                session.syncAttempts = 0
                if (searchPending) 0 else POLL_INTERVAL_MS
            }

            ZcashPassOutcome.Cancelled -> POLL_INTERVAL_MS
            is ZcashPassOutcome.Failed -> if (outcome.published) {
                zcashRestartDelayFor(session.syncAttempts++, RESTART_BASE_MS, RESTART_MAX_MS)
            } else {
                QUIET_RETRY_MS + Random.nextLong(-QUIET_RETRY_JITTER_MS, QUIET_RETRY_JITTER_MS + 1)
            }
        }
    }

    /**
     * Runs immediately before [ZcashSession.sync], so it reuses the SDK-wide sync serialization
     * instead of racing it. Failures are swallowed and only reschedule — discovery must never
     * propagate into [runTick] and kill the loop.
     */
    private suspend fun maybeDiscover(session: ZcashSession) {
        if (!isDiscoveryDue(session.discovery.mark)) return
        val epoch = backgroundManager.foregroundEpoch.value
        val succeeded = tryOrNull { session.discoverForEpoch(epoch) } == true
        session.discovery.mark = ZcashDiscoveryMark(epoch, uptimeProvider.uptime, succeeded)
        tryOrNull { transparentIndexStore.raiseFromHistory(session) }
    }

    /**
     * A sync before the restored rows exist would advance the pass height past their history, and
     * rows created later scan only from there — so a failed preparation skips the turn.
     */
    private suspend fun prepareTransparentIndex(session: ZcashSession): Boolean =
        loggingFailure("Transparent index preparation") { transparentIndexStore.prepare(session) } != null

    /** Foreground and online only. Returns whether more of the search is due right away. */
    private suspend fun searchAddresses(session: ZcashSession): Boolean {
        val deadline = uptimeProvider.uptime + SEARCH_TURN_BUDGET_MS
        var pending = false
        while (backgroundManager.inForeground && !session.isNetworkPaused()) {
            val step = loggingFailure("Address search chunk") { addressSearch.runStep(session) }
            pending = step == ZcashAddressSearchStep.MORE_PENDING
            if (!pending || uptimeProvider.uptime >= deadline) break
        }
        return pending
    }

    /** Null when [block] failed; the failure must never propagate into [runTick] and kill the loop. */
    private suspend fun <T> loggingFailure(operation: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            zcashLogger.w { "$operation failed error=${e.zcashErrorName}" }
            null
        }

    /**
     * A rate limit, not a second memo — [ZcashSession.discoverForEpoch] is the single authority
     * on whether a walk is warranted. A new foreground epoch is debounced by 30s; a failure inside
     * the same epoch backs off to the slower 3-minute cadence instead of retrying on every turn.
     */
    private fun isDiscoveryDue(mark: ZcashDiscoveryMark?): Boolean {
        if (!backgroundManager.inForeground) return false
        if (mark == null) return true
        val epoch = backgroundManager.foregroundEpoch.value
        val interval = if (!mark.succeeded && mark.epoch == epoch) DISCOVERY_RETRY_MS else DISCOVERY_DEBOUNCE_MS
        return uptimeProvider.uptime - mark.lastAttemptUptime >= interval
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val RESTART_BASE_MS = 15_000L
        const val RESTART_MAX_MS = 120_000L
        const val QUIET_RETRY_MS = 5_000L
        const val QUIET_RETRY_JITTER_MS = 1_000L
        const val DISCOVERY_DEBOUNCE_MS = 30_000L
        const val DISCOVERY_RETRY_MS = 3 * 60 * 1000L

        /** A chunk is cheap next to the sync that follows it, but a search must not hold the sync off for long. */
        const val SEARCH_TURN_BUDGET_MS = 20_000L
    }
}
