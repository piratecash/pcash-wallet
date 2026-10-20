package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.shared.desktop.vault.ChangePasswordOutcome
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.PasswordRequirement
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmation: String = "",
    val unmetRequirements: List<PasswordRequirement> = PasswordRequirement.entries,
    val invalidCharacters: Boolean = false,
    val inProgress: Boolean = false,
    val outcome: ChangePasswordOutcome? = null,
) {
    val mismatch: Boolean get() = confirmation.isNotEmpty() && confirmation != newPassword

    val canSubmit: Boolean
        get() = currentPassword.isNotEmpty() &&
            newPassword.isNotEmpty() &&
            unmetRequirements.isEmpty() &&
            !invalidCharacters &&
            confirmation == newPassword &&
            !inProgress
}

class ChangePasswordViewModel(
    private val vault: DesktopVault,
    private val passphraseValidator: PassphraseValidator,
) : ViewModel() {

    var uiState by mutableStateOf(ChangePasswordUiState())
        private set

    fun onCurrentPasswordChange(value: String) {
        uiState = uiState.copy(currentPassword = value, outcome = null)
    }

    fun onNewPasswordChange(value: String) {
        uiState = uiState.copy(
            newPassword = value,
            unmetRequirements = passphraseValidator.unmetRequirements(value),
            invalidCharacters = !passphraseValidator.containsValidCharacters(value),
            outcome = null,
        )
    }

    fun onConfirmationChange(value: String) {
        uiState = uiState.copy(confirmation = value, outcome = null)
    }

    fun onChangeClick() {
        if (!uiState.canSubmit) return
        uiState = uiState.copy(inProgress = true, outcome = null)
        viewModelScope.launch {
            // The vault takes ownership of both arrays and wipes them, whatever the outcome.
            val outcome = vault.changePassword(
                old = uiState.currentPassword.toCharArray(),
                new = uiState.newPassword.toCharArray(),
            )
            uiState = uiState.copy(inProgress = false, outcome = outcome)
        }
    }
}
