package cash.p.terminal.modules.market.tvl

import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.market.MarketModule
import cash.p.terminal.modules.market.tvl.TvlModule.SelectorDialogState
import cash.p.terminal.modules.market.tvl.TvlModule.TvlDiffType
import cash.p.terminal.resources.MarketGlobalMetrics_TvlInDefi
import cash.p.terminal.resources.MarketGlobalMetrics_TvlInDefiDescription
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.entities.ViewState
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.models.HsTimePeriod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class TvlViewModel(
    private val service: TvlService,
    private val tvlViewItemFactory: TvlViewItemFactory,
) : ViewModelUiState<TvlModule.UiState>() {

    private var tvlDiffType = TvlDiffType.Percent
    private var viewState: ViewState = ViewState.Loading
    private var tvlData: TvlModule.TvlData? = null
    private var isRefreshing = false
    private var chainSelectorDialogState: SelectorDialogState = SelectorDialogState.Closed

    val header = MarketModule.Header(
        title = Translator.getString(Res.string.MarketGlobalMetrics_TvlInDefi),
        description = Translator.getString(Res.string.MarketGlobalMetrics_TvlInDefiDescription),
        icon = ImageSource.Remote("https://cdn.blocksdecoded.com/header-images/tvl@3x.png")
    )

    init {
        viewModelScope.launch {
            service.marketTvlItemsObservable.asFlow().collect { tvlItemsDataState ->
                tvlItemsDataState.viewState?.let { viewState = it }

                tvlItemsDataState.dataOrNull?.let {
                    tvlData = tvlViewItemFactory.tvlData(service.chain, service.chains, service.sortDescending, it)
                }
                emitState()
            }
        }

        service.start()
    }

    override fun createState() = TvlModule.UiState(
        viewState = viewState,
        tvlData = tvlData,
        tvlDiffType = tvlDiffType,
        isRefreshing = isRefreshing,
        chainSelectorDialogState = chainSelectorDialogState,
    )

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

    fun onSelectChain(chain: TvlModule.Chain) {
        service.chain = chain
        chainSelectorDialogState = SelectorDialogState.Closed
        emitState()
    }

    fun onToggleSortType() {
        service.sortDescending = !service.sortDescending
    }

    fun onToggleTvlDiffType() {
        tvlDiffType = if (tvlDiffType == TvlDiffType.Percent) TvlDiffType.Currency else TvlDiffType.Percent
        emitState()
    }

    fun onClickChainSelector() {
        chainSelectorDialogState = SelectorDialogState.Opened(Select(service.chain, service.chains))
        emitState()
    }

    fun onChainSelectorDialogDismiss() {
        chainSelectorDialogState = SelectorDialogState.Closed
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

    fun onSelectChartInterval(chartInterval: HsTimePeriod?) {
        service.updateChartInterval(chartInterval)
    }
}
