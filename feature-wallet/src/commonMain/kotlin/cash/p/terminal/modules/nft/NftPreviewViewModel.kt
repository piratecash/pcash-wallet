package cash.p.terminal.modules.nft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class NftPreviewUiState(
    val hidden: Boolean = false,
    val canReveal: Boolean = false,
    val transferAvailability: NftTransferAvailability = NftTransferAvailability.Loading,
)

class NftPreviewViewModel(
    source: NftCollectionSource,
    balanceHiddenManager: IBalanceHiddenManager,
    private val transferAvailabilityProvider: NftTransferAvailabilityProvider,
) : ViewModel() {

    private val hiddenFlow = source.hiddenFlow(balanceHiddenManager)
    private var revealed = false
    private var transferAvailability: NftTransferAvailability = NftTransferAvailability.Loading
    private var transferCheckJob: Job? = null

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

    /** The NFT may have been listed or transferred while the page was away. */
    fun onResume(item: NftItemRecord) {
        transferCheckJob?.cancel()
        updateTransferAvailability(NftTransferAvailability.Loading)
        transferCheckJob = viewModelScope.launch {
            updateTransferAvailability(transferAvailabilityProvider.check(item))
        }
    }

    private fun updateTransferAvailability(availability: NftTransferAvailability) {
        transferAvailability = availability
        uiState = buildState()
    }

    private fun buildState() = NftPreviewUiState(
        hidden = hiddenFlow.value && !revealed,
        canReveal = hiddenFlow.value,
        transferAvailability = transferAvailability,
    )
}
