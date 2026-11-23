package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.featureStacking.ui.PirateInvestmentChartService
import cash.p.terminal.featureStacking.ui.StakingChartDisplayMode
import cash.p.terminal.featureStacking.ui.StakingIncomePoint
import cash.p.terminal.featureStacking.ui.StakingIncomeSnapshot
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.staking_chart_mode_both
import cash.p.terminal.resources.staking_chart_no_fiat_estimate
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.chartview.chart.ChartUiState
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.chartview.chart.SelectedItem
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

abstract class StackingCoinChartViewModel(
    private val service: PirateInvestmentChartService,
    private val numberFormatter: IAppNumberFormatter,
    dispatcherProvider: DispatcherProvider,
) : ChartViewModel(
    service = service,
    valueFormatter = ChartValueShortFormatterSignificant(service, numberFormatter),
    numberFormatter = numberFormatter,
    dispatcherProvider = dispatcherProvider,
    considerAlwaysPositive = true
) {
    private val balanceHiddenManager: IBalanceHiddenManager by inject(IBalanceHiddenManager::class.java)

    // The superclass constructor already calls createState(); this stays null until our fields are set.
    private var incomeService: PirateInvestmentChartService? = service

    private var ticker: String = ""

    var displayModeTabs by mutableStateOf(emptyList<TabItem<StakingChartDisplayMode>>())
        private set

    abstract val coinCode: String

    init {
        viewModelScope.launch {
            balanceHiddenManager.balanceHiddenFlow.collectLatest {
                setTitleHidden(it)
            }
        }
    }

    fun setStakingSource(address: String, token: Token) {
        ticker = token.coin.code
        updateDisplayModeTabs()
        service.setData(investCode = coinCode, coinUid = token.coin.uid, address = address)
    }

    fun onSelectDisplayMode(mode: StakingChartDisplayMode) {
        service.setDisplayMode(mode)
        updateDisplayModeTabs()
    }

    fun refreshIncome() {
        service.invalidate()
        refresh()
    }

    fun getSelectedIncomePoint(selectedItem: SelectedItem): ChartModule.ChartHeaderView {
        val header = getSelectedPoint(selectedItem).copy(extraData = null)
        val snapshot = service.shownIncome ?: return header
        val point = snapshot.points.find { it.timestampSec == selectedItem.timestamp } ?: return header
        return header.withLabels(snapshot, point)
    }

    override fun createState(): ChartUiState {
        val state = super.createState()
        val income = incomeService ?: return state

        updateDisplayModeTabs()
        val snapshot = income.shownIncome
        val lastPoint = snapshot?.points?.lastOrNull {
            snapshot.mode != StakingChartDisplayMode.FIAT || it.fiat != null
        }
        val defaultHeader = state.chartHeaderView?.copy(extraData = null)
        val header = when {
            snapshot == null -> defaultHeader
            lastPoint != null -> defaultHeader?.withLabels(snapshot, lastPoint)
            // FIAT mode with no rated point: the chart is empty, but the header still explains why.
            snapshot.points.isNotEmpty() -> ChartModule.ChartHeaderView(
                value = snapshot.noFiatEstimate(),
                valueHint = null,
                date = null,
                diff = null,
                extraData = null,
            )
            else -> defaultHeader
        }
        val chartInfoData = if (state.titleHidden) {
            state.chartInfoData?.copy(maxValue = null, minValue = null)
        } else {
            state.chartInfoData
        }
        return state.copy(chartHeaderView = header, chartInfoData = chartInfoData)
    }

    private fun ChartModule.ChartHeaderView.withLabels(
        snapshot: StakingIncomeSnapshot,
        point: StakingIncomePoint,
    ): ChartModule.ChartHeaderView {
        val (value, hint) = snapshot.labels(point, ticker, snapshot.noFiatEstimate(), numberFormatter)
        return copy(value = value, valueHint = hint)
    }

    private fun StakingIncomeSnapshot.noFiatEstimate() =
        Translator.getString(Res.string.staking_chart_no_fiat_estimate, currency.code)

    private fun updateDisplayModeTabs() {
        val selected = service.displayMode
        val currencyCode = (service.shownIncome?.currency ?: service.currency).code
        displayModeTabs = listOf(
            StakingChartDisplayMode.COINS to ticker,
            StakingChartDisplayMode.FIAT to currencyCode,
            StakingChartDisplayMode.BOTH to Translator.getString(Res.string.staking_chart_mode_both),
        ).map { (mode, title) -> TabItem(title, mode == selected, mode) }
    }
}
