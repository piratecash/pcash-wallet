package cash.p.terminal.core.managers

import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BeamLifecycleCoordinator(
    private val backgroundManager: BackgroundManager,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val connectivityManager: ConnectivityManager,
    private val offlineModeManager: OfflineModeManager,
) {
    enum class Lease { Polling, CriticalOperation }

    fun bind(
        accountId: String,
        scope: CoroutineScope,
        requestNetwork: (Boolean) -> Deferred<Unit>,
    ) = Binding(OfflineKey(accountId, BlockchainType.Beam), scope, requestNetwork)

    inner class Binding internal constructor(
        private val key: OfflineKey,
        scope: CoroutineScope,
        private val requestNetwork: (Boolean) -> Deferred<Unit>,
    ) {
        private val monitor = Any()
        private val job = SupervisorJob(scope.coroutineContext[Job])
        private val leases = mutableMapOf<Lease, Int>()
        private val operations = mutableSetOf<Job>()
        private var connectivityLease: AutoCloseable? = null
        private var activated = false
        private var paused = false
        private var desired: Boolean? = null
        private var transition: Deferred<Unit>? = null

        init {
            CoroutineScope(scope.coroutineContext + job).launch {
                combine(
                    backgroundManager.stateFlow,
                    backgroundKeepAliveManager.keepAliveBlockchains,
                    connectivityManager.isConnected,
                    offlineModeManager.effectiveFlow,
                    offlineModeManager.stateFlow,
                ) { _, _, _, _, _ -> Unit }.collect { reconcile() }
            }
        }

        fun resume() = synchronized(monitor) {
            if (!job.isActive) return@synchronized
            activated = true
            paused = false
            reconcile()
        }

        fun pause(): Deferred<Unit> = synchronized(monitor) {
            paused = true
            desired = false
            requestNetwork(false).also { transition = it }
        }

        fun stop() = job.cancel()

        suspend fun retry(shouldRestart: () -> Boolean) {
            val pending = synchronized(monitor) {
                if (!canRun) return
                // Share an in-flight start; a failed/completed attempt can be explicitly rearmed.
                if (transition?.isCompleted != false && shouldRestart()) {
                    desired = true
                    transition = requestNetwork(true)
                }
                transition
            }
            pending?.await()
        }

        // The send caller owns the NonCancellable durable handoff boundary; the lease itself
        // must still honor manual offline and terminal account teardown.
        suspend fun <T> withLease(lease: Lease, block: suspend () -> T): T = coroutineScope {
            val operation = currentCoroutineContext().job
            val cancellation = job.invokeOnCompletion { operation.cancel() }
            var acquired = false
            try {
                acquire(lease, operation)
                acquired = true
                connectivityManager.refreshAndAwaitValidation()
                synchronized(monitor) { reconcile() }
                awaitTransition()
                block()
            } finally {
                try {
                    if (acquired) withContext(NonCancellable) { release(lease, operation)?.await() }
                } finally {
                    cancellation.dispose()
                }
            }
        }

        suspend fun awaitOperations() {
            synchronized(monitor) { operations.toList() }.joinAll()
        }

        private fun acquire(lease: Lease, operation: Job) = synchronized(monitor) {
            job.ensureActive()
            if (leases.isEmpty()) connectivityLease = connectivityManager.acquireMonitoringLease()
            leases[lease] = (leases[lease] ?: 0) + 1
            operations += operation
            activated = true
            reconcile()
        }

        private fun release(lease: Lease, operation: Job): Deferred<Unit>? = synchronized(monitor) {
            operations -= operation
            leases[lease]?.let { count ->
                if (count == 1) leases.remove(lease) else leases[lease] = count - 1
            }
            if (leases.isEmpty()) {
                connectivityLease?.close()
                connectivityLease = null
            }
            val previous = transition
            reconcile()
            // A connectivity loss may already have requested stop before this final release.
            if (!hasLease() && desired == false) transition else transition?.takeIf { it !== previous }
        }

        val canRun: Boolean get() = synchronized(monitor) {
            job.isActive && activated && !paused && !offlineModeManager.isNetworkPaused(key) &&
                offlineModeManager.stateFlow.value[key]?.offline != true &&
                connectivityManager.isConnected.value && hasLease()
        }

        private fun reconcile() = synchronized(monitor) {
            if (!job.isActive || !activated) return@synchronized
            val running = canRun
            if (desired != running) {
                desired = running
                transition = requestNetwork(running)
            }
        }

        private suspend fun awaitTransition() {
            synchronized(monitor) { transition }?.await()
        }

        private fun hasLease() = leases.isNotEmpty() ||
            backgroundManager.stateFlow.value == BackgroundManagerState.EnterForeground ||
            BlockchainType.Beam in backgroundKeepAliveManager.keepAliveBlockchains.value
    }
}
