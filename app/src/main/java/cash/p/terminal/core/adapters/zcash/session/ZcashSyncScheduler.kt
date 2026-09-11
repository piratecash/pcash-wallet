package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.adapters.zcash.zcashRestartDelayFor
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.zcash.SyncState
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
            val pause = when {
                offlineModeManager.isNetworkPaused(session.accountId, BlockchainType.Zcash) ->
                    POLL_INTERVAL_MS

                else -> runTick(session)
            }
            delay(pause)
        }
    }

    private fun rotate(): ZcashSession? {
        val session = queue.removeFirstOrNull()
        if (session == null) loop = null else queue += session
        return session
    }

    /** Returns how long to wait before the next turn. */
    private suspend fun runTick(session: ZcashSession): Long {
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

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val RESTART_BASE_MS = 15_000L
        const val RESTART_MAX_MS = 120_000L
    }
}
