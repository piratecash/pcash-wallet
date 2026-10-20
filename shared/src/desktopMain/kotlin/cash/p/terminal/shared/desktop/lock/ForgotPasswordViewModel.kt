package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val understood: Boolean = false,
    val inProgress: Boolean = false,
    val failed: Boolean = false,
) {
    val canSubmit: Boolean get() = understood && !inProgress
}

class ForgotPasswordViewModel(private val vault: DesktopVault) : ViewModel() {

    var uiState by mutableStateOf(ForgotPasswordUiState())
        private set

    fun onUnderstoodChange(value: Boolean) {
        uiState = uiState.copy(understood = value)
    }

    fun onDeleteClick() {
        if (!uiState.canSubmit) return
        uiState = uiState.copy(inProgress = true, failed = false)
        viewModelScope.launch {
            val outcome = vault.wipe()
            uiState = uiState.copy(inProgress = false, failed = outcome == WipeOutcome.FAILED)
        }
    }
}
