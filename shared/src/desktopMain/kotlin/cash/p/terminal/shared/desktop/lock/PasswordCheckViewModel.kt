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

/** [outcome] holds the last failed attempt; a successful one resets the form. */
data class PasswordCheckUiState(
    val password: String = "",
    val inProgress: Boolean = false,
    val outcome: UnlockOutcome? = null,
) {
    val lockedOut: UnlockOutcome.LockedOut? get() = outcome as? UnlockOutcome.LockedOut

    val attemptsLeft: Int? get() = (outcome as? UnlockOutcome.WrongPassword)?.attemptsLeft

    val canSubmit: Boolean get() = password.isNotEmpty() && !inProgress && lockedOut == null
}

/** A password form under the vault's shared attempt lockout. */
abstract class PasswordCheckViewModel(
    private val vault: DesktopVault,
    private val currentDateProvider: ICurrentDateProvider,
) : ViewModel() {

    var uiState by mutableStateOf(PasswordCheckUiState())
        protected set

    /** The vault takes ownership of [password] and wipes it, whatever the outcome. */
    protected abstract suspend fun check(password: CharArray): UnlockOutcome

    protected open fun onPasswordAccepted() = Unit

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

    fun onSubmitClick() {
        if (!uiState.canSubmit) return
        uiState = uiState.copy(inProgress = true, outcome = null)
        viewModelScope.launch {
            val outcome = check(uiState.password.toCharArray())
            // The app can relock before this screen goes away, and the password must not outlive the check.
            if (outcome == UnlockOutcome.Success) {
                uiState = PasswordCheckUiState()
                onPasswordAccepted()
            } else {
                uiState = uiState.copy(inProgress = false, outcome = outcome)
            }
            (outcome as? UnlockOutcome.LockedOut)?.let { awaitLockoutExpiry(it) }
        }
    }

    /** The deadline is reported once, so the screen itself must become usable again when it passes. */
    private suspend fun awaitLockoutExpiry(lockedOut: UnlockOutcome.LockedOut) {
        currentDateProvider.awaitWallTime { lockedOut.until.time }
        if (uiState.lockedOut == lockedOut) {
            uiState = uiState.copy(outcome = null)
        }
    }
}
