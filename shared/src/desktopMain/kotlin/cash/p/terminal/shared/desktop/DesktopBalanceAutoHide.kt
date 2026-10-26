package cash.p.terminal.shared.desktop

import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import io.horizontalsystems.core.AppForegroundState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** A window that lost focus stays on screen, so balance auto-hide reacts to focus, not to the background. */
class DesktopBalanceAutoHide(
    private val foregroundState: AppForegroundState,
    private val vault: DesktopVault,
    private val balanceHiddenManager: Lazy<BalanceHiddenManager>,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        foregroundState.inForeground.filter { !it }.collect {
            if (vault.state.value is DesktopVaultState.Unlocked) balanceHiddenManager.value.autoHide()
        }
    }
}
