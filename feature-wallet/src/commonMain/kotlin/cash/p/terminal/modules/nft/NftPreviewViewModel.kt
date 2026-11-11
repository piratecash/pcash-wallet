package cash.p.terminal.modules.nft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.nft.NftCollectionPrefsRepository
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftLinkButton
import cash.p.terminal.core.nft.NftLottieLoader
import cash.p.terminal.core.nft.collectionPrefKey
import cash.p.terminal.core.nft.owner
import cash.p.terminal.core.nft.withUserTrust
import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.entities.NftUserTrust
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class NftPreviewUiState(
    val hidden: Boolean = false,
    val canReveal: Boolean = false,
    val transferAvailability: NftTransferAvailability = NftTransferAvailability.Loading,
    val domainExpiry: NftDomainExpiry? = null,
    val trust: NftTrust = NftTrust.Whitelist,
    val menuActions: List<NftAction> = emptyList(),
    val linkButtons: List<NftLinkButton> = emptyList(),
    val onSale: Boolean = false,
    val lottieJson: String? = null,
) {
    val showSpamPlate: Boolean get() = trust == NftTrust.None

    val isSuspicious: Boolean get() = trust == NftTrust.None || trust == NftTrust.Blacklist

    val linkButtonsState: LinkButtonsState
        get() = when {
            linkButtons.isEmpty() || transferAvailability is NftTransferAvailability.Hidden -> LinkButtonsState.Hidden
            transferAvailability is NftTransferAvailability.Available -> LinkButtonsState.Enabled
            else -> LinkButtonsState.Disabled
        }

    /** The user already confirmed: only a hidden balance or a button gone from the metadata cancels it. */
    fun canOpenConfirmedLink(url: String): Boolean = !hidden && linkButtons.any { it.uri == url }

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

enum class LinkButtonsState { Hidden, Disabled, Enabled }

class NftPreviewViewModel(
    source: NftCollectionSource,
    private val item: NftItemRecord,
    balanceHiddenManager: IBalanceHiddenManager,
    private val transferAvailabilityProvider: NftTransferAvailabilityProvider,
    private val repository: NftCollectionsRepository,
    private val prefsRepository: NftCollectionPrefsRepository,
    private val lottieLoader: NftLottieLoader,
) : ViewModel() {

    private val hiddenFlow = source.hiddenFlow(balanceHiddenManager)
    private var revealed = false
    private var transferAvailability: NftTransferAvailability = NftTransferAvailability.Loading
    private var onSale = item.inSale
    private var linkButtons: List<NftLinkButton> = emptyList()
    private var domainExpiry: NftDomainExpiry? = null
    private var pref: NftCollectionPrefRecord? = null
    private var lottieJson: String? = null
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
        viewModelScope.launch {
            prefsRepository.prefsFlow(item.accountId).collect {
                pref = it[item.collectionPrefKey]
                uiState = buildState()
            }
        }
        item.lottieUrl?.let { url ->
            viewModelScope.launch {
                lottieJson = lottieLoader.load(url)
                uiState = buildState()
            }
        }
    }

    fun onToggleReveal() {
        revealed = !revealed
        uiState = buildState()
    }

    /** The NFT may have been listed or transferred while the page was away. */
    fun onResume() {
        transferCheckJob?.cancel()
        updateTransferAvailability(NftTransferAvailability.Loading)
        transferCheckJob = viewModelScope.launch {
            refresh()
            repository.domainExpiryTriggers(setOf(item.owner)).collect { refresh() }
        }
    }

    /** A renewal, a listing or a late kit start can change both, so each trigger re-reads both. */
    private suspend fun refresh() {
        val check = transferAvailabilityProvider.check(item)
        linkButtons = check.buttons
        updateTransferAvailability(check.availability)
        updateDomainExpiry()
    }

    private suspend fun updateDomainExpiry() {
        domainExpiry = transferAvailabilityProvider.domainExpiry(item)
        uiState = buildState()
    }

    private fun updateTransferAvailability(availability: NftTransferAvailability) {
        transferAvailability = availability
        availability.liveSaleState()?.let { onSale = it }
        uiState = buildState()
    }

    fun onHide() = prefsRepository.setHidden(item, true)

    fun onUndoHide() = prefsRepository.setHidden(item, false)

    fun onNotSpam() = prefsRepository.markNotSpam(item)

    fun onReportSpam() = prefsRepository.markSpam(item)

    private fun buildState(): NftPreviewUiState {
        val trust = effectiveTrust()
        val canBurn = transferAvailability is NftTransferAvailability.Available && trust != NftTrust.Whitelist
        return NftPreviewUiState(
            hidden = hiddenFlow.value && !revealed,
            canReveal = hiddenFlow.value,
            transferAvailability = transferAvailability,
            domainExpiry = domainExpiry,
            trust = trust,
            linkButtons = linkButtons,
            onSale = onSale,
            lottieJson = lottieJson,
            menuActions = nftActions(
                trust = trust,
                hasCollection = item.collectionAddress != null,
                canBurn = canBurn,
                hasExplorer = repository.explorerUrl(item.blockchainType, item.address) != null,
            ),
        )
    }

    /** Null when the check did not learn the listing, so the last known flag stands. */
    private fun NftTransferAvailability.liveSaleState(): Boolean? = when (this) {
        is NftTransferAvailability.Available -> false
        // The check tests the listing before the owner, so NotOwner proves the NFT is not listed.
        is NftTransferAvailability.Unavailable -> when (reason) {
            NftTransferUnavailableReason.OnSale -> true
            NftTransferUnavailableReason.NotOwner -> false
            NftTransferUnavailableReason.WatchOnly, NftTransferUnavailableReason.CheckFailed -> null
        }
        else -> null
    }

    private fun effectiveTrust(): NftTrust =
        if (pref?.userTrust == NftUserTrust.Spam) NftTrust.Blacklist else item.withUserTrust(pref).trust
}
