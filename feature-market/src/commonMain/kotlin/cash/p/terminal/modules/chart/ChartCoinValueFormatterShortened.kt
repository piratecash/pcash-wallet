package cash.p.terminal.modules.chart

import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.chartview.chart.ChartModule
import java.math.BigDecimal

class ChartCoinValueFormatterShortened(
    private val fullCoin: FullCoin,
    private val numberFormatter: IAppNumberFormatter,
) : ChartModule.ChartNumberFormatter {

    override fun formatValue(currency: Currency, value: BigDecimal): String {
        return numberFormatter.formatCoinShort(value, fullCoin.coin.code, 8)
    }

}
