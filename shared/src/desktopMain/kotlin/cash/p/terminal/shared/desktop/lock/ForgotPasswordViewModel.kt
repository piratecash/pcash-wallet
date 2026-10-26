package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.shared.desktop.DesktopWipeCoordinator
import cash.p.terminal.shared.desktop.WipeProgress
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val understood: Boolean = false,
    val inProgress: Boolean = false,
) {
    val canSubmit: Boolean get() = understood && !inProgress
}

/** The wipe and the restart after it belong to the process; this screen only asks for them. */
class ForgotPasswordViewModel(private val wipeCoordinator: DesktopWipeCoordinator) : ViewModel() {

    var uiState by mutableStateOf(ForgotPasswordUiState())
        private set

    init {
        viewModelScope.launch {
            wipeCoordinator.progress.collect { progress ->
                uiState = uiState.copy(inProgress = progress != WipeProgress.Idle)
            }
        }
    }

    fun onUnderstoodChange(value: Boolean) {
        uiState = uiState.copy(understood = value)
    }

    fun onDeleteClick() {
        if (uiState.canSubmit) wipeCoordinator.wipe()
    }
}
