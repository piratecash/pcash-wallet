package cash.p.terminal.modules.nft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.nft.DOMAIN_RENEW_SOON_DAYS
import cash.p.terminal.core.nft.NftChange
import cash.p.terminal.core.nft.NftCollectionPrefKey
import cash.p.terminal.core.nft.NftCollectionPrefsRepository
import cash.p.terminal.core.nft.NftCollectionState
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftItemsState
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.core.nft.collectionPrefKey
import cash.p.terminal.core.nft.nftCollectionState
import cash.p.terminal.core.nft.owner
import cash.p.terminal.core.nft.withUserTrust
import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val NEW_EVENT_DEBOUNCE = 2.seconds

/** [items] are the visible NFTs only; [hiddenCollectionsCount] counts hidden and spam collections. */
data class NftCollectionsUiState(
    val items: List<NftItemRecord> = emptyList(),
    val isLoading: Boolean = true,
    val hidden: Boolean = false,
    val networkTitle: String? = null,
    val hiddenCollectionsCount: Int = 0,
    val hasNfts: Boolean = false,
    val expiringDomainAddresses: Set<String> = emptySet(),
) {
    val totalCount: Int get() = items.size
    val isEmpty: Boolean get() = !isLoading && !hasNfts
    val allCollectionsHidden: Boolean get() = !isLoading && items.isEmpty() && hiddenCollectionsCount > 0
}

internal class NftItemsWithPrefs(
    val state: NftItemsState,
    val prefs: Map<NftCollectionPrefKey, NftCollectionPrefRecord>,
)

/** All owners belong to one account, so its preferences apply to every one of them. */
internal fun observeItemsWithPrefs(
    repository: NftCollectionsRepository,
    prefsRepository: NftCollectionPrefsRepository,
    owners: Set<NftOwner>,
): Flow<NftItemsWithPrefs> {
    val prefs = owners.firstOrNull()?.let { prefsRepository.prefsFlow(it.accountId) } ?: flowOf(emptyMap())
    return combine(repository.itemsFlow(owners), prefs, ::NftItemsWithPrefs)
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NftCollectionsViewModel(
    private val source: NftCollectionSource,
    private val repository: NftCollectionsRepository,
    private val prefsRepository: NftCollectionPrefsRepository,
    walletManager: IWalletManager,
    private val balanceHiddenManager: IBalanceHiddenManager,
) : ViewModel() {

    private val hiddenFlow = source.hiddenFlow(balanceHiddenManager)
    private var currentOwners: Set<NftOwner>? = null
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Not in the UI state: Wallet.toString() would print key material.
    var receiveWallet: Wallet? by mutableStateOf(null)
        private set

    var uiState by mutableStateOf(NftCollectionsUiState(hidden = hiddenFlow.value))
        private set

    init {
        viewModelScope.launch {
            launch { hiddenFlow.collect { uiState = uiState.copy(hidden = it) } }

            val wallets = source.walletsFlow(walletManager)
                .onEach { receiveWallet = it.firstOrNull(repository::supportsSection) }
            repository.ownersFlow(wallets)
                .onEach(::onOwnersChanged)
                .flatMapLatest(::observe)
                .collect()
        }
    }

    fun onResume() {
        resumes.tryEmit(Unit)
        refreshCurrentOwners(force = false)
    }

    fun refresh() = refreshCurrentOwners(force = true)

    fun toggleHidden() = source.toggleHidden(balanceHiddenManager)

    private fun onOwnersChanged(owners: Set<NftOwner>) {
        currentOwners = owners
        val networkTitle = owners.map { it.blockchainType }.distinct()
            .mapNotNull(repository::networkTitle)
            .joinToString()
            .ifEmpty { null }
        val retained = uiState.items.filter { it.owner in owners }
        uiState = uiState.copy(
            items = retained,
            isLoading = retained.isEmpty(),
            networkTitle = networkTitle,
            expiringDomainAddresses = emptySet(),
        )
    }

    private fun observe(owners: Set<NftOwner>): Flow<Any> = merge(
        observeItemsWithPrefs(repository, prefsRepository, owners).onEach(::showItems),
        refreshRequests(owners).onEach { force -> repository.refresh(owners, force) },
        repository.expiringDomainsFlow(owners, DOMAIN_RENEW_SOON_DAYS, resumes).onEach(::showExpiringDomains),
    )

    private fun showExpiringDomains(domains: List<NftDomainExpiry>) {
        uiState = uiState.copy(expiringDomainAddresses = domains.mapTo(mutableSetOf()) { it.nftAddress })
    }

    private fun showItems(data: NftItemsWithPrefs) {
        val (visible, notVisible) = data.state.items.partition {
            it.collectionState(data) == NftCollectionState.Visible
        }
        uiState = uiState.copy(
            items = visible.map { it.withUserTrust(data.prefs[it.collectionPrefKey]) },
            isLoading = !data.state.isLoaded,
            hiddenCollectionsCount = notVisible.distinctBy { it.collectionPrefKey }.size,
            hasNfts = data.state.items.isNotEmpty(),
        )
    }

    private fun NftItemRecord.collectionState(data: NftItemsWithPrefs) =
        nftCollectionState(trust, data.prefs[collectionPrefKey])

    /** Emits whether the refresh must bypass the repository throttle. */
    private fun refreshRequests(owners: Set<NftOwner>): Flow<Boolean> {
        fun changes() = owners.map(repository::changes).merge()
        return merge(
            flowOf(false),
            changes().filterIsInstance<NftChange.KitAppeared>().map { false },
            repository.networkResumed(owners).map { false },
            changes().filterIsInstance<NftChange.NewEvent>().debounce(NEW_EVENT_DEBOUNCE).map { true },
        )
    }

    private fun refreshCurrentOwners(force: Boolean) {
        val owners = currentOwners ?: return
        viewModelScope.launch { repository.refresh(owners, force) }
    }
}
