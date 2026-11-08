package cash.p.terminal.shared.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.pin.DuressSecretController
import kotlinx.coroutines.launch

/** [available] is false inside a hidden wallet, where nothing may show that duress exists. */
data class DuressSectionUiState(val available: Boolean = false, val set: Boolean = false)

class DesktopDuressSectionViewModel(private val controller: DuressSecretController) : ViewModel() {

    var uiState by mutableStateOf(DuressSectionUiState())
        private set

    init {
        onResume()
    }

    fun onResume() {
        viewModelScope.launch { uiState = read() }
    }

    fun onDisableClick() {
        viewModelScope.launch {
            controller.disableDuress()
            uiState = read()
        }
    }

    private suspend fun read() = DuressSectionUiState(
        available = controller.isDuressAvailable(),
        set = controller.isDuressSet(),
    )
}
