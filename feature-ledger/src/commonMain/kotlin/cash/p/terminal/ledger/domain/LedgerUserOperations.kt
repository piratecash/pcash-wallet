package cash.p.terminal.ledger.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Device work the user is waiting for; background syncs give the device up to it. */
class LedgerUserOperations {
    private val state = MutableStateFlow(State(running = 0, yieldedApps = emptySet()))
    private val _yieldedNotices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _waitNotices = MutableSharedFlow<LedgerWaitNotice>(extraBufferCapacity = 8)
    private val viewKeyExports = MutableStateFlow(0)

    @Volatile
    private var remindingNotice: LedgerWaitNotice? = null

    val isActive: Boolean get() = state.value.running > 0

    /** App names whose background sync gave the device up; emitted once the user operation is over. */
    val yieldedNotices: SharedFlow<String> = _yieldedNotices.asSharedFlow()

    /** Why the device is held while a user operation waits; queued notices that went stale are dropped. */
    val waitNotices: Flow<LedgerWaitNotice> = _waitNotices.filter { it == remindingNotice && isActive }

    /** True while a Monero wallet open waits for the user to allow the view-key export on the device. */
    val awaitingViewKeyExport: Flow<Boolean> = viewKeyExports.map { it > 0 }.distinctUntilChanged()

    // Non-suspending: the open runs in a plain device-session block. Nested pairs count as one.
    fun beginViewKeyExport() = viewKeyExports.update { it + 1 }

    fun endViewKeyExport() = viewKeyExports.update { it - 1 }

    /** Nested runs count as one operation that ends with the outermost. */
    suspend fun <T> run(block: suspend () -> T): T {
        state.update { it.copy(running = it.running + 1) }
        try {
            return block()
        } finally {
            finish()
        }
    }

    fun reportYielded(appName: String) {
        val before = state.getAndUpdate {
            if (it.running > 0) it.copy(yieldedApps = it.yieldedApps + appName) else it
        }
        if (before.running == 0) _yieldedNotices.tryEmit(appName)
    }

    /** Runs until cancelled: reminds every user operation that waits too long why the device is held. */
    suspend fun remindWhileUsersWait(notice: LedgerWaitNotice) {
        remindingNotice = notice
        try {
            while (true) {
                state.first { it.running > 0 }
                var untilNotice = FIRST_WAIT_NOTICE_AFTER
                while (!userOperationEndsWithin(untilNotice)) {
                    _waitNotices.tryEmit(notice)
                    untilNotice = WAIT_NOTICE_INTERVAL
                }
            }
        } finally {
            remindingNotice = null
        }
    }

    private suspend fun userOperationEndsWithin(timeout: Duration): Boolean =
        withTimeoutOrNull(timeout) { state.first { it.running == 0 } } != null

    // Non-suspending, so it also completes in `finally` of a cancelled run.
    private fun finish() {
        val before = state.getAndUpdate {
            if (it.running == 1) State(running = 0, yieldedApps = emptySet()) else it.copy(running = it.running - 1)
        }
        if (before.running == 1) before.yieldedApps.forEach { _yieldedNotices.tryEmit(it) }
    }

    private data class State(val running: Int, val yieldedApps: Set<String>)

    private companion object {
        val FIRST_WAIT_NOTICE_AFTER = 1500.milliseconds
        val WAIT_NOTICE_INTERVAL = 10.seconds
    }
}

enum class LedgerWaitReason { Sync, DeviceRequest }

data class LedgerWaitNotice(val appName: String, val reason: LedgerWaitReason)
