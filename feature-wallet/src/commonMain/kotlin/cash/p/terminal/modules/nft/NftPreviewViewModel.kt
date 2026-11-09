package cash.p.terminal.modules.nft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.launch

data class NftPreviewUiState(
    val hidden: Boolean = false,
    val canReveal: Boolean = false,
)

class NftPreviewViewModel(
    source: NftCollectionSource,
    balanceHiddenManager: IBalanceHiddenManager,
) : ViewModel() {

    private val hiddenFlow = source.hiddenFlow(balanceHiddenManager)
    private var revealed = false

    var uiState by mutableStateOf(buildState())
        private set

    init {
        viewModelScope.launch {
            hiddenFlow.collect {
                revealed = false
                uiState = buildState()
            }
        }
    }

    fun onToggleReveal() {
        revealed = !revealed
        uiState = buildState()
    }

    private fun buildState() = NftPreviewUiState(
        hidden = hiddenFlow.value && !revealed,
        canReveal = hiddenFlow.value,
    )
}
