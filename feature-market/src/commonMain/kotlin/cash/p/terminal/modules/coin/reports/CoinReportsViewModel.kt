package cash.p.terminal.modules.coin.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.reports.CoinReportsModule.ReportViewItem
import io.horizontalsystems.core.helpers.DateHelper
import cash.p.terminal.wallet.models.CoinReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class CoinReportsViewModel(private val service: CoinReportsService) : ViewModel() {
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _reportViewItems = MutableStateFlow<List<ReportViewItem>?>(null)
    val reportViewItems: StateFlow<List<ReportViewItem>?> = _reportViewItems

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

    private fun handleServiceState(state: DataState<List<CoinReport>>) {
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

    private fun sync(reports: List<CoinReport>) {
        _reportViewItems.value = reports.map { viewItem(it) }
    }

    private fun viewItem(report: CoinReport): ReportViewItem {
        return ReportViewItem(
            author = report.author,
            title = report.title,
            body = report.body,
            date = DateHelper.formatDate(report.date, "MMM dd, yyyy"),
            url = report.url
        )
    }
}
