package cash.p.terminal.modules.coin.treasuries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.core.IAppNumberFormatter
import cash.p.terminal.core.logoUrl
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesModule.CoinTreasuriesData
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesModule.CoinTreasuryItem
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesModule.SelectorDialogState
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesModule.TreasuryTypeFilter
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.wallet.models.CoinTreasury
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class CoinTreasuriesViewModel(
    private val service: CoinTreasuriesService,
    private val numberFormatter: IAppNumberFormatter
) : ViewModel() {
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _coinTreasuries = MutableStateFlow<CoinTreasuriesData?>(null)
    val coinTreasuries: StateFlow<CoinTreasuriesData?> = _coinTreasuries

    private val _treasuryTypeSelectorDialogState = MutableStateFlow<SelectorDialogState>(SelectorDialogState.Closed)
    val treasuryTypeSelectorDialogState: StateFlow<SelectorDialogState> = _treasuryTypeSelectorDialogState

    init {
        viewModelScope.launch {
            service.stateObservable.asFlow()
                .catch {
                    _viewState.value = ViewState.Error(it)
                }
                .collect { state ->
                    handleServiceState(state)
                }
        }

        service.start()
    }

    private fun handleServiceState(state: DataState<List<CoinTreasury>>) {
        state.dataOrNull?.let {
            _viewState.value = ViewState.Success
            syncCoinTreasuriesData(it)
        }

        state.errorOrNull?.let {
            _viewState.value = ViewState.Error(it)
        }
    }

    fun refresh() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    fun onErrorClick() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    fun onToggleSortType() {
        service.sortDescending = !service.sortDescending
    }

    fun onClickTreasuryTypeSelector() {
        _treasuryTypeSelectorDialogState.value = 
            SelectorDialogState.Opened(Select(service.treasuryType, service.treasuryTypes))
        
    }

    fun onSelectTreasuryType(type: TreasuryTypeFilter) {
        service.treasuryType = type
        _treasuryTypeSelectorDialogState.value = SelectorDialogState.Closed
    }

    fun onTreasuryTypeSelectorDialogDismiss() {
        _treasuryTypeSelectorDialogState.value = SelectorDialogState.Closed
    }

    override fun onCleared() {
        service.stop()
    }

    private fun refreshWithMinLoadingSpinnerPeriod() {
        service.refresh()
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(1000)
            _isRefreshing.value = false
        }
    }

    private fun syncCoinTreasuriesData(coinTreasuries: List<CoinTreasury>) {
        val coinTreasuriesData = CoinTreasuriesData(
            Select(service.treasuryType, service.treasuryTypes),
            service.sortDescending,
            coinTreasuries.map {
                coinTreasuryItem(it)
            }
        )
        _coinTreasuries.value = coinTreasuriesData
    }

    private fun coinTreasuryItem(coinTreasury: CoinTreasury) =
        CoinTreasuryItem(
            fund = coinTreasury.fund,
            fundLogoUrl = coinTreasury.logoUrl,
            country = coinTreasury.countryCode,
            amount = numberFormatter.formatCoinShort(coinTreasury.amount, service.coin.code, 8),
            amountInCurrency = numberFormatter.formatFiatShort(
                coinTreasury.amountInCurrency,
                service.currency.symbol,
                2
            )
        )
}
