package cash.p.terminal.modules.zcashnetwork

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.managers.ZcashServer
import cash.p.terminal.core.managers.ZcashServerManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

class ZcashNetworkViewModel(
    private val serverManager: ZcashServerManager
) : ViewModel() {

    var uiState by mutableStateOf(buildUiState())
        private set

    init {
        merge(serverManager.serversUpdatedFlow, serverManager.serverSelectedFlow)
            .onEach { uiState = buildUiState() }
            .launchIn(viewModelScope)
    }

    fun onSelectAuto() = serverManager.selectAuto()

    fun onSelect(url: String) = serverManager.select(url)

    fun onDelete(url: String) = serverManager.delete(url)

    private fun buildUiState(): UiState {
        val isAuto = serverManager.isAuto
        val current = serverManager.current
        val selectedUrl = current.url.takeUnless { isAuto }
        return UiState(
            isAuto = isAuto,
            autoServerName = current.name.takeIf { isAuto },
            defaultItems = serverManager.defaultServers.map { it.toViewItem(selectedUrl) },
            customItems = serverManager.customServers.map { it.toViewItem(selectedUrl) },
        )
    }

    private fun ZcashServer.toViewItem(selectedUrl: String?) = ViewItem(name, url, selected = url == selectedUrl)

    data class ViewItem(val name: String, val url: String, val selected: Boolean)

    data class UiState(
        val isAuto: Boolean = true,
        val autoServerName: String? = null,
        val defaultItems: List<ViewItem> = emptyList(),
        val customItems: List<ViewItem> = emptyList(),
    )
}
