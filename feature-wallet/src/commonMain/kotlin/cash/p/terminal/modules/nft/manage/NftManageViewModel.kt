package cash.p.terminal.modules.nft.manage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.nft.NftCollectionPrefKey
import cash.p.terminal.core.nft.NftCollectionPrefsRepository
import cash.p.terminal.core.nft.NftCollectionState
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.collectionPrefKey
import cash.p.terminal.core.nft.nftCollectionState
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.modules.nft.NftCollectionSource
import cash.p.terminal.modules.nft.NftItemsWithPrefs
import cash.p.terminal.modules.nft.observeItemsWithPrefs
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

private const val COLLAPSED_VISIBLE_ROWS = 3

/** One managed collection; an NFT outside any collection is a row of its own. [item] is the group's first NFT. */
data class NftManageRow(
    val item: NftItemRecord,
    val nftCount: Int,
    val state: NftCollectionState,
) {
    val key: NftCollectionPrefKey get() = item.collectionPrefKey
    val isSingle: Boolean get() = item.collectionAddress == null
    val isUnnamed: Boolean get() = isSingle && item.name.isNullOrBlank()
    val isSpam: Boolean get() = state == NftCollectionState.SpamByUser || state == NftCollectionState.SpamByProvider
}

data class NftManageUiState(
    val rows: List<NftManageRow> = emptyList(),
    val isLoading: Boolean = true,
    val hidden: Boolean = false,
    val showAllVisible: Boolean = false,
) {
    val visibleRows: List<NftManageRow> get() = rows.filter { it.state == NftCollectionState.Visible }
    val hiddenRows: List<NftManageRow> get() = rows.filter { it.state == NftCollectionState.Hidden }
    val spamRows: List<NftManageRow> get() = rows.filter { it.isSpam }

    val canCollapseVisible: Boolean
        get() = visibleRows.size > COLLAPSED_VISIBLE_ROWS && hiddenRows.isNotEmpty()

    val shownVisibleRows: List<NftManageRow>
        get() = visibleRows.let { if (canCollapseVisible && !showAllVisible) it.take(COLLAPSED_VISIBLE_ROWS) else it }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NftManageViewModel(
    source: NftCollectionSource,
    repository: NftCollectionsRepository,
    private val prefsRepository: NftCollectionPrefsRepository,
    walletManager: IWalletManager,
    balanceHiddenManager: IBalanceHiddenManager,
) : ViewModel() {

    private val hiddenFlow = source.hiddenFlow(balanceHiddenManager)

    var uiState by mutableStateOf(NftManageUiState(hidden = hiddenFlow.value))
        private set

    init {
        viewModelScope.launch {
            launch { hiddenFlow.collect { uiState = uiState.copy(hidden = it) } }
            repository.ownersFlow(source.walletsFlow(walletManager))
                .flatMapLatest { observeItemsWithPrefs(repository, prefsRepository, it) }
                .collect(::showItems)
        }
    }

    // A collection moved between sections must not land in the collapsed part of the visible list.
    fun onVisibleChange(row: NftManageRow, visible: Boolean) {
        prefsRepository.setHidden(row.item, !visible)
        uiState = uiState.copy(showAllVisible = true)
    }

    fun onNotSpam(row: NftManageRow) {
        prefsRepository.markNotSpam(row.item)
        uiState = uiState.copy(showAllVisible = true)
    }

    fun onToggleShowAllVisible() {
        uiState = uiState.copy(showAllVisible = !uiState.showAllVisible)
    }

    private fun showItems(data: NftItemsWithPrefs) {
        val rows = data.state.items.groupBy { it.collectionPrefKey }.map { (key, nfts) ->
            NftManageRow(nfts.first(), nfts.size, nftCollectionState(nfts.groupTrust(), data.prefs[key]))
        }
        uiState = uiState.copy(rows = rows, isLoading = !data.state.isLoaded)
    }

    // Lists hide a blacklisted NFT on its own, so its group must land in Spam to offer "Not spam".
    private fun List<NftItemRecord>.groupTrust(): NftTrust =
        if (any { it.trust == NftTrust.Blacklist }) NftTrust.Blacklist else first().trust
}
