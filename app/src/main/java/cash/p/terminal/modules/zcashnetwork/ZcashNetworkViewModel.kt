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

    fun onSelect(url: String) = serverManager.select(url)

    fun onDelete(url: String) = serverManager.delete(url)

    private fun buildUiState(): UiState {
        val currentUrl = serverManager.current.url
        return UiState(
            defaultItems = serverManager.defaultServers.map { it.toViewItem(currentUrl) },
            customItems = serverManager.customServers.map { it.toViewItem(currentUrl) },
        )
    }

    private fun ZcashServer.toViewItem(currentUrl: String) = ViewItem(name, url, selected = url == currentUrl)

    data class ViewItem(val name: String, val url: String, val selected: Boolean)

    data class UiState(
        val defaultItems: List<ViewItem> = emptyList(),
        val customItems: List<ViewItem> = emptyList(),
    )
}
