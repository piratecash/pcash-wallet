package cash.p.terminal.modules.chart

import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.core.entities.Currency
import java.math.BigDecimal

class ChartCurrencyValueFormatterShortened(
    private val numberFormatter: IAppNumberFormatter,
) : ChartModule.ChartNumberFormatter {

    override fun formatValue(currency: Currency, value: BigDecimal): String {
        return numberFormatter.formatFiatShort(value, currency.symbol, 2)
    }

}
