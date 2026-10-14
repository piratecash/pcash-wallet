package cash.p.terminal.modules.market.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import cash.p.terminal.wallet.entities.FullCoin
import kotlinx.coroutines.launch

class MarketSearchViewModel(
    private val marketFavoritesManager: MarketFavoritesManager,
    private val marketSearchService: MarketSearchService,
    private val marketDiscoveryService: MarketDiscoveryService,
) : ViewModel() {
    private var searchState = marketSearchService.stateFlow.value
    private var discoveryState = marketDiscoveryService.stateFlow.value
    private var listId: String = ""
    private var favoriteCoinUids: Set<String> = emptySet()
    private var page: Page = Page.Discovery(
        recent = coinItems(discoveryState.recent),
        popular = coinItems(discoveryState.popular),
    )

    var uiState by mutableStateOf(UiState(page, listId))
        private set

    init {
        viewModelScope.launch {
            marketSearchService.stateFlow.collect {
                handleUpdatedSearchState(it)
            }
        }
        viewModelScope.launch {
            marketDiscoveryService.stateFlow.collect {
                handleUpdatedDiscoveryState(it)
            }
        }
        viewModelScope.launch {
            marketFavoritesManager.favoriteCoinUids.collect {
                favoriteCoinUids = it
                emitState()
            }
        }

        viewModelScope.launch {
            marketDiscoveryService.start()
        }
    }

    private fun handleUpdatedDiscoveryState(discoveryState: MarketDiscoveryService.State) {
        this.discoveryState = discoveryState

        emitState()
    }

    private fun handleUpdatedSearchState(searchState: MarketSearchService.State) {
        this.searchState = searchState

        emitState()
    }

    fun searchByQuery(query: String) {
        viewModelScope.launch {
            marketSearchService.setQuery(query)
        }
    }

    private fun coinItems(fullCoins: List<FullCoin>) =
        fullCoins.map {
            MarketSearchModule.CoinItem(
                it,
                it.coin.uid in favoriteCoinUids
            )
        }

    private fun emitState() {
        if (searchState.query.isNotBlank()) {
            page = Page.SearchResults(coinItems(searchState.results))
            listId = searchState.query
        } else {
            page = Page.Discovery(
                coinItems(discoveryState.recent),
                coinItems(discoveryState.popular),
            )
            listId = ""
        }

        viewModelScope.launch {
            uiState = UiState(page, listId)
        }
    }

    fun onFavoriteClick(favourited: Boolean, coinUid: String) {
        viewModelScope.launch {
            if (favourited) {
                marketFavoritesManager.remove(coinUid)
            } else {
                marketFavoritesManager.add(coinUid)
            }
        }
    }

    fun onCoinOpened(coin: Coin) {
        marketDiscoveryService.addCoinToRecent(coin)
    }

    data class UiState(
        val page: Page,
        val listId: String
    )

    sealed class Page {
        data class Discovery(
            val recent: List<MarketSearchModule.CoinItem>, val popular: List<MarketSearchModule.CoinItem>
        ) : Page()

        data class SearchResults(val items: List<MarketSearchModule.CoinItem>) : Page()
    }
}
