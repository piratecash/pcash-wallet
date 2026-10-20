package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.shared.desktop.vault.CreateOutcome
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.PasswordRequirement
import kotlinx.coroutines.launch

data class CreatePasswordUiState(
    val password: String = "",
    val confirmation: String = "",
    val unmetRequirements: List<PasswordRequirement> = PasswordRequirement.entries,
    val invalidCharacters: Boolean = false,
    val understood: Boolean = false,
    val inProgress: Boolean = false,
    val failed: Boolean = false,
) {
    val mismatch: Boolean get() = confirmation.isNotEmpty() && confirmation != password

    val canSubmit: Boolean
        get() = unmetRequirements.isEmpty() &&
            !invalidCharacters &&
            confirmation == password &&
            understood &&
            !inProgress
}

class CreatePasswordViewModel(
    private val vault: DesktopVault,
    private val passphraseValidator: PassphraseValidator,
) : ViewModel() {

    var uiState by mutableStateOf(CreatePasswordUiState())
        private set

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(
            password = value,
            unmetRequirements = passphraseValidator.unmetRequirements(value),
            invalidCharacters = !passphraseValidator.containsValidCharacters(value),
            failed = false,
        )
    }

    fun onConfirmationChange(value: String) {
        uiState = uiState.copy(confirmation = value, failed = false)
    }

    fun onUnderstoodChange(value: Boolean) {
        uiState = uiState.copy(understood = value)
    }

    fun onCreateClick() {
        if (!uiState.canSubmit) return
        uiState = uiState.copy(inProgress = true, failed = false)
        viewModelScope.launch {
            // The vault takes ownership of the array and wipes it, whatever the outcome.
            val outcome = vault.create(uiState.password.toCharArray())
            uiState = uiState.copy(inProgress = false, failed = outcome == CreateOutcome.FAILED)
        }
    }
}
