package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import io.horizontalsystems.core.ICurrentDateProvider
import kotlinx.coroutines.launch

/** [outcome] holds the last failed attempt; a successful one takes the screen down. */
data class UnlockUiState(
    val password: String = "",
    val inProgress: Boolean = false,
    val outcome: UnlockOutcome? = null,
) {
    val lockedOut: UnlockOutcome.LockedOut? get() = outcome as? UnlockOutcome.LockedOut

    val canSubmit: Boolean get() = password.isNotEmpty() && !inProgress && lockedOut == null
}

class UnlockViewModel(
    private val vault: DesktopVault,
    private val currentDateProvider: ICurrentDateProvider,
) : ViewModel() {

    var uiState by mutableStateOf(UnlockUiState())
        private set

    init {
        viewModelScope.launch {
            val until = vault.lockedOutUntil() ?: return@launch
            val lockedOut = UnlockOutcome.LockedOut(until)
            uiState = uiState.copy(outcome = lockedOut)
            awaitLockoutExpiry(lockedOut)
        }
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, outcome = uiState.lockedOut)
    }

    fun onUnlockClick() {
        if (!uiState.canSubmit) return
        uiState = uiState.copy(inProgress = true, outcome = null)
        viewModelScope.launch {
            // The vault takes ownership of the array and wipes it, whatever the outcome.
            val outcome = vault.unlock(uiState.password.toCharArray())
            // The app can relock before this screen goes away, and the password must not outlive the unlock.
            uiState = if (outcome == UnlockOutcome.Success) {
                UnlockUiState()
            } else {
                uiState.copy(inProgress = false, outcome = outcome)
            }
            (outcome as? UnlockOutcome.LockedOut)?.let { awaitLockoutExpiry(it) }
        }
    }

    /** Shown once: returning from the reset screen must land on a plain unlock form. */
    fun onDataUnreadableShown() {
        uiState = uiState.copy(outcome = null)
    }

    /** The deadline is reported once, so the screen itself must become usable again when it passes. */
    private suspend fun awaitLockoutExpiry(lockedOut: UnlockOutcome.LockedOut) {
        currentDateProvider.awaitWallTime { lockedOut.until.time }
        if (uiState.lockedOut == lockedOut) {
            uiState = uiState.copy(outcome = null)
        }
    }
}
