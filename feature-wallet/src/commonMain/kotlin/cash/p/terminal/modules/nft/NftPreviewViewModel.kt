package cash.p.terminal.modules.nft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.owner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class NftPreviewUiState(
    val hidden: Boolean = false,
    val canReveal: Boolean = false,
    val transferAvailability: NftTransferAvailability = NftTransferAvailability.Loading,
    val domainExpiry: NftDomainExpiry? = null,
) {
    val renewButton: RenewButtonState
        get() = when {
            domainExpiry == null -> RenewButtonState.Hidden
            transferAvailability is NftTransferAvailability.Hidden -> RenewButtonState.Hidden
            transferAvailability == NftTransferAvailability.Unavailable(NftTransferUnavailableReason.WatchOnly) ->
                RenewButtonState.Hidden
            transferAvailability is NftTransferAvailability.Available && !domainExpiry.isOnSale ->
                RenewButtonState.Enabled
            else -> RenewButtonState.Disabled
        }
}

enum class RenewButtonState { Hidden, Disabled, Enabled }

class NftPreviewViewModel(
    source: NftCollectionSource,
    balanceHiddenManager: IBalanceHiddenManager,
    private val transferAvailabilityProvider: NftTransferAvailabilityProvider,
    private val repository: NftCollectionsRepository,
) : ViewModel() {

    private val hiddenFlow = source.hiddenFlow(balanceHiddenManager)
    private var revealed = false
    private var transferAvailability: NftTransferAvailability = NftTransferAvailability.Loading
    private var domainExpiry: NftDomainExpiry? = null
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
            refresh(item)
            repository.domainExpiryTriggers(setOf(item.owner)).collect { refresh(item) }
        }
    }

    /** A renewal, a listing or a late kit start can change both, so each trigger re-reads both. */
    private suspend fun refresh(item: NftItemRecord) {
        updateTransferAvailability(transferAvailabilityProvider.check(item))
        updateDomainExpiry(item)
    }

    private suspend fun updateDomainExpiry(item: NftItemRecord) {
        domainExpiry = transferAvailabilityProvider.domainExpiry(item)
        uiState = buildState()
    }

    private fun updateTransferAvailability(availability: NftTransferAvailability) {
        transferAvailability = availability
        uiState = buildState()
    }

    private fun buildState() = NftPreviewUiState(
        hidden = hiddenFlow.value && !revealed,
        canReveal = hiddenFlow.value,
        transferAvailability = transferAvailability,
        domainExpiry = domainExpiry,
    )
}
