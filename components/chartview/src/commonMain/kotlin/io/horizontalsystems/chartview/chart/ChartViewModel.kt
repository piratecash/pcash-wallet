package io.horizontalsystems.chartview.chart

import androidx.lifecycle.viewModelScope
import cash.p.terminal.resources.CoinPage_TimeDuration_All
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.components.TabItem
import io.horizontalsystems.chartview.ChartData
import io.horizontalsystems.chartview.entity.ChartInfoData
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.Value
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.entities.viewState
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.models.HsTimePeriod
import io.horizontalsystems.core.models.stringResId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import java.util.Date

open class ChartViewModel(
    private val service: AbstractChartService,
    private val valueFormatter: ChartModule.ChartNumberFormatter,
    private val numberFormatter: IAppNumberFormatter,
    private val dispatcherProvider: DispatcherProvider,
    private val considerAlwaysPositive: Boolean = false,
) : ViewModelUiState<ChartUiState>() {

    private var tabItems = listOf<TabItem<HsTimePeriod?>>()
    private var chartHeaderView: ChartModule.ChartHeaderView? = null
    private var chartInfoData: ChartInfoData? = null
    private var loading = false
    private var titleHidden = false
    private var viewState: ViewState = ViewState.Success

    init {
        loading = true
        emitState()

        viewModelScope.launch {
            service.chartTypeObservable.asFlow().collect { chartType ->
                val tabItems = service.chartIntervals.map {
                    val titleRes = it?.stringResId ?: Res.string.CoinPage_TimeDuration_All
                    TabItem(Translator.getString(titleRes), it == chartType.orElse(null), it)
                }
                this@ChartViewModel.tabItems = tabItems

                emitState()
            }
        }

        viewModelScope.launch {
            service.chartPointsWrapperObservable.asFlow().collect { chartItemsDataState ->
                chartItemsDataState.viewState?.let {
                    viewState = it
                }

                loading = false

                syncChartItems(chartItemsDataState.getOrNull())

                emitState()
            }
        }

        viewModelScope.launch(dispatcherProvider.io) {
            service.start()
        }
    }

    protected fun setTitleHidden(titleHidden: Boolean) {
        this.titleHidden = titleHidden
        emitState()
    }

    override fun createState() = ChartUiState(
        tabItems = tabItems,
        chartHeaderView = chartHeaderView,
        chartInfoData = chartInfoData,
        loading = loading,
        viewState = viewState,
        hasVolumes = service.hasVolumes,
        chartViewType = service.chartViewType,
        considerAlwaysPositive = considerAlwaysPositive,
        titleHidden = titleHidden
    )

    fun onSelectChartInterval(chartInterval: HsTimePeriod?) {
        loading = true
        viewModelScope.launch {
            // Solution to prevent flickering.
            //
            // When items are loaded fast for chartInterval change
            // it shows loading state for a too little period of time.
            // It looks like a flickering.
            // It is true for most cases. Updating UI with some delay resolves it.
            // Since it is true for the most cases here we set delay.
            delay(300)
            emitState()
        }

        service.updateChartInterval(chartInterval)
    }

    fun refresh() {
        loading = true
        emitState()

        service.refresh()
    }

    private fun syncChartItems(chartPointsWrapper: ChartPointsWrapper?) {
        if (chartPointsWrapper == null || chartPointsWrapper.items.isEmpty()) {
            chartHeaderView = null
            chartInfoData = null

            return
        }

        val chartData = ChartData(
            chartPointsWrapper.items,
            chartPointsWrapper.isMovementChart,
            false,
            chartPointsWrapper.indicators
        )

        val headerView = if (!chartPointsWrapper.isMovementChart) {
            val value = valueFormatter.formatValue(service.currency, chartData.sum())
            ChartModule.ChartHeaderView(
                value = value,
                valueHint = null,
                date = null,
                diff = null,
                extraData = null
            )
        } else {
            val chartItems = chartPointsWrapper.items
            val latestItem = chartItems.last()

            val currentPrice = chartPointsWrapper.currentPrice ?: latestItem.value.toBigDecimal()
            val currentValue = valueFormatter.formatValue(service.currency, currentPrice)

            val dominanceData = latestItem.dominance?.let { dominance ->
                val earliestItem = chartItems.first()
                val diff = earliestItem.dominance?.let { earliestDominance ->
                    Value.Percent((dominance - earliestDominance).toBigDecimal())
                }

                ChartModule.ChartHeaderExtraData.Dominance(
                    numberFormatter.format(dominance, 0, 2, suffix = "%"),
                    diff
                )
            }
            ChartModule.ChartHeaderView(
                value = currentValue,
                valueHint = null,
                date = null,
                diff = Value.Percent(chartData.diff()),
                extraData = dominanceData
            )
        }

        val (minValue, maxValue) = getMinMax(chartData.minValue, chartData.maxValue)

        val chartInfoData = ChartInfoData(
            chartData,
            maxValue,
            minValue
        )

        this.chartHeaderView = headerView
        this.chartInfoData = chartInfoData
    }

    private val noChangesLimitPercent = 0.2f
    private fun getMinMax(minValue: Float, maxValue: Float): Pair<String?, String?> {
        var max = maxValue
        var min = minValue

        if (max == min) {
            min *= (1 - noChangesLimitPercent)
            max *= (1 + noChangesLimitPercent)
        }

        val maxValueStr = getFormattedValue(max, service.currency)
        val minValueStr = getFormattedValue(min, service.currency)

        return Pair(minValueStr, maxValueStr)

    }

    private fun getFormattedValue(value: Float, currency: Currency): String {
        return valueFormatter.formatValue(currency, value.toBigDecimal())
    }

    override fun onCleared() {
        service.stop()
    }

    fun getSelectedPoint(selectedItem: SelectedItem): ChartModule.ChartHeaderView {
        val value =
            valueFormatter.formatValue(service.currency, selectedItem.mainValue.toBigDecimal())
        val dayAndTime = DateHelper.getFullDate(Date(selectedItem.timestamp * 1000))

        return ChartModule.ChartHeaderView(
            value = value,
            valueHint = null,
            date = dayAndTime,
            diff = null,
            extraData = getItemExtraData(selectedItem)
        )
    }

    private fun getItemExtraData(item: SelectedItem): ChartModule.ChartHeaderExtraData? {
        val movingAverages = item.movingAverages
        val rsi = item.rsi
        val macd = item.macd
        val dominance = item.dominance
        val volume = item.volume

        return when {
            movingAverages.isNotEmpty() || rsi != null || macd != null -> {
                ChartModule.ChartHeaderExtraData.Indicators(movingAverages, rsi, macd)
            }

            dominance != null -> {
                ChartModule.ChartHeaderExtraData.Dominance(
                    numberFormatter.format(dominance, 0, 2, suffix = "%"),
                    null
                )
            }

            volume != null -> ChartModule.ChartHeaderExtraData.Volume(
                numberFormatter.formatFiatShort(
                    volume.toBigDecimal(),
                    service.currency.symbol,
                    2
                )
            )

            else -> null
        }
    }
}
