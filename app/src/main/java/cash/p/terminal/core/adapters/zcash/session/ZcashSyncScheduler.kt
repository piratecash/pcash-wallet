package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.adapters.zcash.zcashRestartDelayFor
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.zcash.SyncState
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the open sessions one at a time. The SDK serializes syncs process-wide anyway, so the
 * queue is what decides whose turn it is instead of leaving them to fight over the lock.
 */
class ZcashSyncScheduler(
    private val offlineModeManager: OfflineModeManager,
    private val backgroundManager: BackgroundManager,
    private val uptimeProvider: UptimeProvider,
    dispatcherProvider: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val mutex = Mutex()
    private val queue = mutableListOf<ZcashSession>()
    private var loop: Job? = null

    suspend fun enqueue(session: ZcashSession) = mutex.withLock {
        if (queue.none { it === session }) queue += session
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
    }

    suspend fun remove(session: ZcashSession) {
        mutex.withLock { queue.removeAll { it === session } }
    }

    private suspend fun runLoop() {
        while (true) {
            val session = mutex.withLock { rotate() } ?: return
            val pause = if (session.isNetworkPaused()) POLL_INTERVAL_MS else runTick(session)
            delay(pause)
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
        maybeDiscover(session)
        // The walk may have taken long enough for offline mode to arrive in the meantime.
        if (session.isNetworkPaused()) return POLL_INTERVAL_MS
        if (session.sync() is ZcashSessionResult.Unavailable) {
            remove(session)
            return 0
        }
        if (session.state.value.syncState !is SyncState.Failed) {
            session.syncAttempts = 0
            return POLL_INTERVAL_MS
        }
        return zcashRestartDelayFor(session.syncAttempts++, RESTART_BASE_MS, RESTART_MAX_MS)
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
        const val DISCOVERY_DEBOUNCE_MS = 30_000L
        const val DISCOVERY_RETRY_MS = 3 * 60 * 1000L
    }
}
