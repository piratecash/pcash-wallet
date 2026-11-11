package cash.p.terminal.modules.nft.renew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.nft.DOMAIN_RENEW_SOON_DAYS
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.modules.nft.NftCollectionSource
import cash.p.terminal.modules.nft.shownBy
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.supportsTonConnect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Domains expiring soon for [owners]; [deselected] are the not-on-sale ones the user unchecked.
 * [hidden] follows the eye of the balance screen's NFT section.
 */
data class NftDomainRenewalState(
    val owners: Set<NftOwner> = emptySet(),
    val domains: List<NftDomainExpiry> = emptyList(),
    val deselected: Set<String> = emptySet(),
    val hidden: Boolean = false,
) {
    val selected: List<NftDomainExpiry> get() = domains.filter { !it.isOnSale && it.nftAddress !in deselected }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NftDomainRenewalViewModel(
    private val repository: NftCollectionsRepository,
    walletManager: IWalletManager,
    balanceHiddenManager: IBalanceHiddenManager,
    settings: BalanceSettingsStorage,
) : ViewModel() {

    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val hiddenFlow = NftCollectionSource.AllWallets.hiddenFlow(balanceHiddenManager)

    var uiState by mutableStateOf(NftDomainRenewalState(hidden = hiddenFlow.value))
        private set

    init {
        viewModelScope.launch { hiddenFlow.collect { uiState = uiState.copy(hidden = it) } }
        val wallets = walletManager.activeWalletsFlow.map { list ->
            list.filter { !it.account.isWatchAccount && it.account.supportsTonConnect() }
                // Same wallets NftOwnerWalletResolver accepts, so every listed domain can be confirmed.
                .filter(repository::supportsSection)
        }.shownBy(settings)
        viewModelScope.launch {
            repository.ownersFlow(wallets)
                .onEach { uiState = NftDomainRenewalState(owners = it, hidden = uiState.hidden) }
                .flatMapLatest(::reads)
                .collect()
        }
    }

    fun onResume() {
        resumes.tryEmit(Unit)
    }

    fun onToggle(domain: NftDomainExpiry) {
        val address = domain.nftAddress
        val deselected = uiState.deselected
        uiState = uiState.copy(deselected = if (address in deselected) deselected - address else deselected + address)
    }

    private fun reads(owners: Set<NftOwner>) = repository
        .expiringDomainsFlow(owners, DOMAIN_RENEW_SOON_DAYS, resumes)
        .onEach { domains -> uiState = uiState.copy(domains = domains) }
}
