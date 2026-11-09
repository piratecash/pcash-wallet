package cash.p.terminal.modules.nft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.nft.NftChange
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftItemsState
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.core.nft.owner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val NEW_EVENT_DEBOUNCE = 2.seconds

data class NftCollectionsUiState(
    val items: List<NftItemRecord> = emptyList(),
    val isLoading: Boolean = true,
    val hidden: Boolean = false,
    val networkTitle: String? = null,
) {
    val totalCount: Int get() = items.size
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NftCollectionsViewModel(
    private val source: NftCollectionSource,
    private val repository: NftCollectionsRepository,
    walletManager: IWalletManager,
    private val balanceHiddenManager: IBalanceHiddenManager,
) : ViewModel() {

    private val hiddenFlow = source.hiddenFlow(balanceHiddenManager)
    private var currentOwners: Set<NftOwner>? = null

    var uiState by mutableStateOf(NftCollectionsUiState(hidden = hiddenFlow.value))
        private set

    init {
        viewModelScope.launch {
            launch { hiddenFlow.collect { uiState = uiState.copy(hidden = it) } }

            val wallets = when (source) {
                NftCollectionSource.AllWallets -> walletManager.activeWalletsFlow
                is NftCollectionSource.SingleWallet -> flowOf(listOf(source.wallet))
            }
            wallets
                .map { repository.owners(it) }
                .distinctUntilChanged()
                .onEach(::onOwnersChanged)
                .flatMapLatest(::observe)
                .collect()
        }
    }

    fun onResume() = refreshCurrentOwners(force = false)

    fun refresh() = refreshCurrentOwners(force = true)

    fun toggleHidden() = source.toggleHidden(balanceHiddenManager)

    private fun onOwnersChanged(owners: Set<NftOwner>) {
        currentOwners = owners
        val networkTitle = owners.map { it.blockchainType }.distinct()
            .mapNotNull(repository::networkTitle)
            .joinToString()
            .ifEmpty { null }
        val retained = uiState.items.filter { it.owner in owners }
        uiState = uiState.copy(items = retained, isLoading = retained.isEmpty(), networkTitle = networkTitle)
    }

    private fun observe(owners: Set<NftOwner>): Flow<Any> = merge(
        repository.itemsFlow(owners).onEach(::showItems),
        refreshRequests(owners).onEach { force -> repository.refresh(owners, force) },
    )

    private fun showItems(state: NftItemsState) {
        uiState = uiState.copy(items = state.items, isLoading = !state.isLoaded)
    }

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
