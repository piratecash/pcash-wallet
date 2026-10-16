package cash.p.terminal.modules.markdown.localreader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.feature.wallet.LocalizedAssetReader
import cash.p.terminal.ui_compose.entities.ViewState

class MarkdownLocalViewModel(
    private val localizedAssetReader: LocalizedAssetReader,
) : ViewModel() {

    var markdownContent by mutableStateOf<String?>(null)
        private set

    var viewState by mutableStateOf<ViewState>(ViewState.Loading)
        private set

    fun parseContent(content: String) {
        markdownContent = content
        viewState = ViewState.Success
    }

    suspend fun loadAsset(prefix: String) {
        parseContent(localizedAssetReader.read(prefix))
    }

}
