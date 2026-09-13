package cash.p.terminal.feature.miniapp.ui.miniapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage

class MiniAppViewModel(
    private val codeStorage: IUniqueCodeStorage
) : ViewModel() {

    var uiState by mutableStateOf(MiniAppUiState())
        private set

    fun updateConnectionStatus() {
        uiState = uiState.copy(isConnected = codeStorage.connectedAccountId.isNotBlank())
    }
}

data class MiniAppUiState(
    val isConnected: Boolean = false
)
