package cash.p.terminal.shared.desktop

import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WipeProgress {
    data object Idle : WipeProgress
    data object InProgress : WipeProgress

    /** The app could not start itself again; [outcome] decides what the user is told. */
    data class RestartManually(val outcome: WipeOutcome) : WipeProgress
}

/**
 * Wipes and restarts in the process scope: the wipe recomposes away the screen that asked for it.
 * The restart follows any outcome, since the wipe closes the session first and nothing may reopen it in this process.
 */
class DesktopWipeCoordinator(
    private val vault: DesktopVault,
    private val relauncher: DesktopAppRelauncher,
    private val appExit: DesktopAppExit,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val _progress = MutableStateFlow<WipeProgress>(WipeProgress.Idle)
    val progress: StateFlow<WipeProgress> = _progress.asStateFlow()

    fun start(scope: CoroutineScope): Job = scope.launch(dispatcherProvider.io) {
        requests.consumeEach { wipeAndRestart() }
    }

    fun wipe() {
        if (_progress.compareAndSet(WipeProgress.Idle, WipeProgress.InProgress)) requests.trySend(Unit)
    }

    private suspend fun wipeAndRestart() {
        val outcome = vault.wipe()
        if (relauncher.relaunch(wipeFailed = outcome == WipeOutcome.FAILED)) {
            appExit.request()
        } else {
            _progress.value = WipeProgress.RestartManually(outcome)
        }
    }
}
