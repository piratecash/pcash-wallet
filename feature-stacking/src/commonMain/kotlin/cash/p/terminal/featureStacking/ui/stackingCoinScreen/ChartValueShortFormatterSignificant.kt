package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import cash.p.terminal.featureStacking.ui.PirateInvestmentChartService
import cash.p.terminal.featureStacking.ui.StakingChartDisplayMode
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import java.math.BigDecimal

class ChartValueShortFormatterSignificant(
    private val service: PirateInvestmentChartService,
    private val numberFormatter: IAppNumberFormatter,
) : ChartModule.ChartNumberFormatter {
    override fun formatValue(currency: Currency, value: BigDecimal): String {
        val snapshot = service.shownIncome
        return if (snapshot?.mode == StakingChartDisplayMode.FIAT) {
            numberFormatter.formatFiatShort(value, snapshot.currency.symbol, snapshot.currency.decimal)
        } else {
            numberFormatter.formatNumberShort(value, COIN_LABEL_FRACTION_DIGITS)
        }
    }
}
