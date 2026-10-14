package cash.p.terminal.modules.market.platform

import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import androidx.lifecycle.viewModelScope
import cash.p.terminal.resources.MarketPlatformCoins_PlatformEcosystem
import cash.p.terminal.resources.MarketPlatformCoins_PlatformEcosystemDescription
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.core.iconUrl
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.modules.market.MarketItem
import cash.p.terminal.modules.market.MarketModule
import cash.p.terminal.modules.market.MarketViewItem
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.modules.market.sort
import cash.p.terminal.modules.market.topplatforms.Platform
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketPlatformViewModel(
    platform: Platform,
    private val repository: MarketPlatformCoinsRepository,
    private val favoritesManager: MarketFavoritesManager,
    private val numberFormatter: IAppNumberFormatter,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModelUiState<MarketPlatformUiState>() {

    val sortingFields = listOf(
        SortingField.HighestCap,
        SortingField.LowestCap,
        SortingField.TopGainers,
        SortingField.TopLosers,
    )

    private var sortingField: SortingField = SortingField.HighestCap
    private var viewState: ViewState = ViewState.Loading
    private var viewItems: List<MarketViewItem> = listOf()
    private var cache: List<MarketItem> = emptyList()
    private var isRefreshing = false

    val header = MarketModule.Header(
        Translator.getString(
            Res.string.MarketPlatformCoins_PlatformEcosystem, platform.name
        ),
        Translator.getString(
            Res.string.MarketPlatformCoins_PlatformEcosystemDescription,
            platform.name
        ),
        ImageSource.Remote(platform.iconUrl)
    )

    init {
        viewModelScope.launch { sync() }
    }

    override fun createState() = MarketPlatformUiState(
        viewItems = viewItems,
        viewState = viewState,
        sortingField = sortingField,
        isRefreshing = isRefreshing,
    )

    fun refresh() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    fun onErrorClick() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    fun onSelectSortingField(sortingField: SortingField) {
        this.sortingField = sortingField
        viewModelScope.launch { sync() }
    }

    fun onAddFavorite(coinUid: String) {
        viewModelScope.launch {
            favoritesManager.add(coinUid)
            sync()
        }
    }

    fun onRemoveFavorite(coinUid: String) {
        viewModelScope.launch {
            favoritesManager.remove(coinUid)
            sync()
        }
    }

    private suspend fun sync(forceRefresh: Boolean = false) = withContext(dispatcherProvider.io) {
        if (!forceRefresh && cache.isNotEmpty()) {
            val favoriteCoinUids = favoritesManager.getAll()
            viewItems = cache
                .sort(sortingField)
                .map { item ->
                    marketViewItem(item, favoriteCoinUids)
                }
            viewState = ViewState.Success
            emitState()
        } else {
            fetchFromRepository(forceRefresh)
        }
    }

    private suspend fun fetchFromRepository(forceRefresh: Boolean) {
        try {
            val items = repository.get(sortingField, forceRefresh)
            val favoriteCoinUids = favoritesManager.getAll()
            viewItems = items?.map {
                marketViewItem(it, favoriteCoinUids)
            } ?: listOf()

            viewState = ViewState.Success
        } catch (e: Throwable) {
            viewState = ViewState.Error(e)
        }
        emitState()
    }

    private fun marketViewItem(item: MarketItem, favoriteCoinUids: List<String>): MarketViewItem =
        MarketViewItem.create(
            marketItem = item,
            favorited = favoriteCoinUids.contains(item.fullCoin.coin.uid),
            numberFormatter = numberFormatter
        )

    // The spinner must not wait for the fetch: it runs alongside it, as it did before.
    private fun refreshWithMinLoadingSpinnerPeriod() {
        viewModelScope.launch {
            launch { sync(true) }

            isRefreshing = true
            delay(1000)
            isRefreshing = false
            emitState()
        }
    }
}

data class MarketPlatformUiState(
    val viewItems: List<MarketViewItem>,
    val viewState: ViewState,
    val sortingField: SortingField,
    val isRefreshing: Boolean,
)
