package io.horizontalsystems.chartview.chart

import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.Value
import java.math.BigDecimal

object ChartModule {

    fun createViewModel(
        chartService: AbstractChartService,
        chartNumberFormatter: ChartNumberFormatter,
        numberFormatter: IAppNumberFormatter,
        dispatcherProvider: DispatcherProvider,
    ): ChartViewModel {
        return ChartViewModel(chartService, chartNumberFormatter, numberFormatter, dispatcherProvider)
    }

    interface ChartNumberFormatter {
        fun formatValue(currency: Currency, value: BigDecimal): String
    }

    data class ChartHeaderView(
        val value: String,
        val valueHint: String?,
        val date: String?,
        val diff: Value.Percent?,
        val extraData: ChartHeaderExtraData?
    )

    sealed class ChartHeaderExtraData {
        class Volume(val volume: String) : ChartHeaderExtraData()
        class Dominance(val dominance: String, val diff: Value.Percent?) : ChartHeaderExtraData()
        class Indicators(
            val movingAverages: List<SelectedItem.MA>,
            val rsi: Float?,
            val macd: SelectedItem.Macd?
        ) : ChartHeaderExtraData()
    }

}
