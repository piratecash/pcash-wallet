package cash.p.terminal.modules.zcashnetwork.addserver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.core.Caution
import cash.p.terminal.core.managers.AddResult
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.resources.AddEvmSyncSource_Warning_UrlExists
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.zcash_network_invalid_url
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource

class AddZcashServerViewModel(
    private val serverManager: ZcashServerManager
) : ViewModel() {

    var uiState by mutableStateOf(UiState())
        private set

    fun onUrlChange(url: String) {
        uiState = uiState.copy(url = url, caution = null)
    }

    fun onAddClick() {
        uiState = when (serverManager.addCustom(uiState.url)) {
            is AddResult.Added -> uiState.copy(caution = null, closeScreen = true)
            AddResult.Duplicate ->
                uiState.copy(caution = caution(Res.string.AddEvmSyncSource_Warning_UrlExists, Caution.Type.Warning))
            AddResult.Invalid ->
                uiState.copy(caution = caution(Res.string.zcash_network_invalid_url, Caution.Type.Error))
        }
    }

    private fun caution(textRes: StringResource, type: Caution.Type) = Caution(Translator.getString(textRes), type)

    data class UiState(
        val url: String = "",
        val caution: Caution? = null,
        val closeScreen: Boolean = false,
    )
}
