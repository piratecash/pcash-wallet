package cash.p.terminal.modules.coin.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.core.IAppNumberFormatter
import cash.p.terminal.core.logoUrl
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.investments.CoinInvestmentsModule.FundViewItem
import cash.p.terminal.modules.coin.investments.CoinInvestmentsModule.ViewItem
import io.horizontalsystems.core.helpers.DateHelper
import cash.p.terminal.wallet.models.CoinInvestment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class CoinInvestmentsViewModel(
    private val service: CoinInvestmentsService,
    private val numberFormatter: IAppNumberFormatter
) : ViewModel() {
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _viewItems = MutableStateFlow<List<ViewItem>?>(null)
    val viewItems: StateFlow<List<ViewItem>?> = _viewItems

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

    private fun handleServiceState(state: DataState<List<CoinInvestment>>) {
        state.dataOrNull?.let {
            _viewState.value = ViewState.Success
            sync(it)
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

    private fun sync(investments: List<CoinInvestment>) {
        _viewItems.value = investments.map { viewItem(it) }
    }

    private fun viewItem(investment: CoinInvestment): ViewItem {
        val amount = investment.amount?.let {
            numberFormatter.formatFiatShort(it, service.usdCurrency.symbol, 2)
        } ?: "---"
        val dateString = DateHelper.formatDate(investment.date, "MMM dd, yyyy")

        return ViewItem(
            amount = amount,
            info = "${investment.round} - $dateString",
            fundViewItems = investment.funds.map {
                FundViewItem(it.name, it.logoUrl, it.isLead, it.website)
            }
        )
    }
}
