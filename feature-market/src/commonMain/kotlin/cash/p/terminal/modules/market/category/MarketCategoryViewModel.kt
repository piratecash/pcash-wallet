package cash.p.terminal.modules.market.category

import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.market.MarketField
import cash.p.terminal.modules.market.MarketModule
import cash.p.terminal.modules.market.MarketViewItem
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.modules.market.topcoins.SelectorDialogState
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class MarketCategoryViewModel(
    private val service: MarketCategoryService,
    private val numberFormatter: IAppNumberFormatter,
) : ViewModelUiState<MarketCategoryUiState>() {

    private val marketFields = MarketField.values().toList()
    private val header = MarketModule.Header(
        service.coinCategoryName,
        service.coinCategoryDescription,
        ImageSource.Remote(service.coinCategoryImageUrl)
    )
    private var marketItems: List<MarketItemWrapper> = listOf()
    private var marketField = MarketField.PriceDiff
    private var viewState: ViewState = ViewState.Loading
    private var viewItems: List<MarketViewItem>? = null
    private var isRefreshing = false
    private var selectorDialogState: SelectorDialogState = SelectorDialogState.Closed

    init {
        viewModelScope.launch {
            service.stateObservable.asFlow().collect {
                syncState(it)
            }
        }

        service.start()
    }

    override fun createState() = MarketCategoryUiState(
        header = header,
        menu = MarketCategoryModule.Menu(
            Select(service.sortingField, service.sortingFields),
            Select(marketField, marketFields)
        ),
        viewState = viewState,
        viewItems = viewItems,
        isRefreshing = isRefreshing,
        selectorDialogState = selectorDialogState,
    )

    private fun syncState(state: DataState<List<MarketItemWrapper>>) {
        viewState = state.viewState ?: viewState

        state.dataOrNull?.let {
            marketItems = it

            syncMarketViewItems()
        }

        emitState()
    }

    private fun syncMarketViewItems() {
        viewItems = marketItems.map {
            MarketViewItem.create(it.marketItem, numberFormatter, favorited = it.favorited)
        }
    }

    private fun refreshWithMinLoadingSpinnerPeriod() {
        service.refresh()
        viewModelScope.launch {
            isRefreshing = true
            emitState()
            delay(1000)
            isRefreshing = false
            emitState()
        }
    }

    fun onSelectSortingField(sortingField: SortingField) {
        service.setSortingField(sortingField)
        selectorDialogState = SelectorDialogState.Closed
        emitState()
    }

    fun onSelectMarketField(marketField: MarketField) {
        this.marketField = marketField

        syncMarketViewItems()
        emitState()
    }

    fun onSelectorDialogDismiss() {
        selectorDialogState = SelectorDialogState.Closed
        emitState()
    }

    fun showSelectorMenu() {
        selectorDialogState = SelectorDialogState.Opened(Select(service.sortingField, service.sortingFields))
        emitState()
    }

    fun refresh() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    fun onErrorClick() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    override fun onCleared() {
        service.stop()
    }

    fun onAddFavorite(uid: String) {
        service.addFavorite(uid)
    }

    fun onRemoveFavorite(uid: String) {
        service.removeFavorite(uid)
    }
}

data class MarketCategoryUiState(
    val header: MarketModule.Header,
    val menu: MarketCategoryModule.Menu,
    val viewState: ViewState,
    val viewItems: List<MarketViewItem>?,
    val isRefreshing: Boolean,
    val selectorDialogState: SelectorDialogState,
)
