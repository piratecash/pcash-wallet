package cash.p.terminal.shared.desktop

import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DesktopAppForegroundState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IBackgroundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The wallet runs while its window is on screen and unlocked. Focus is not enough to stop it: losing
 * focus to a screenshot tool or another window would zero native balances until the kits restart.
 */
class DesktopBackgroundManager(
    foregroundState: DesktopAppForegroundState,
    vault: DesktopVault,
    dispatcherProvider: DispatcherProvider,
) : IBackgroundManager {

    private val _stateFlow = MutableStateFlow(toState(foregroundState.windowMinimized.value, vault.state.value))
    override val stateFlow: StateFlow<BackgroundManagerState> = _stateFlow.asStateFlow()

    private val _foregroundEpoch = MutableStateFlow(0)
    override val foregroundEpoch: StateFlow<Int> = _foregroundEpoch.asStateFlow()

    override val inForeground: Boolean
        get() = _stateFlow.value == BackgroundManagerState.EnterForeground

    init {
        dispatcherProvider.applicationScope.launch {
            combine(foregroundState.windowMinimized, vault.state, ::toState).collect(::update)
        }
    }

    private fun update(state: BackgroundManagerState) {
        if (state == _stateFlow.value) return
        if (state == BackgroundManagerState.EnterForeground) _foregroundEpoch.update { it + 1 }
        _stateFlow.value = state
    }

    private fun toState(windowMinimized: Boolean, vaultState: DesktopVaultState) =
        if (!windowMinimized && vaultState is DesktopVaultState.Unlocked) {
            BackgroundManagerState.EnterForeground
        } else {
            BackgroundManagerState.EnterBackground
        }
}
