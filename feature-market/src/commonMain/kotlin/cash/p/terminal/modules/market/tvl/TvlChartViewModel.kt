package cash.p.terminal.modules.market.tvl

import cash.p.terminal.modules.chart.ChartCurrencyValueFormatterShortened
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter

class TvlChartViewModel(
    private val tvlChartService: TvlChartService,
    chartCurrencyValueFormatter: ChartCurrencyValueFormatterShortened,
    numberFormatter: IAppNumberFormatter,
    dispatcherProvider: DispatcherProvider,
) : ChartViewModel(tvlChartService, chartCurrencyValueFormatter, numberFormatter, dispatcherProvider) {

    fun onSelectChain(chain: TvlModule.Chain) {
        tvlChartService.chain = chain
    }

}
