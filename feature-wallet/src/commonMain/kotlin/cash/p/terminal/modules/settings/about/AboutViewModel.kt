package cash.p.terminal.modules.settings.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ITermsManager
import io.horizontalsystems.core.ISystemInfoManager
import kotlinx.coroutines.launch

class AboutViewModel(
    private val termsManager: ITermsManager,
    systemInfoManager: ISystemInfoManager,
) : ViewModel() {

    var uiState by mutableStateOf(
        AboutUiState(
            appVersion = systemInfoManager.appVersionDisplay,
            termsShowAlert = !termsManager.allTermsAccepted,
        )
    )
        private set

    init {
        viewModelScope.launch {
            termsManager.termsAcceptedSignalFlow.collect { allAccepted ->
                uiState = uiState.copy(termsShowAlert = !allAccepted)
            }
        }
    }
}

data class AboutUiState(
    val appVersion: String,
    val termsShowAlert: Boolean,
)
