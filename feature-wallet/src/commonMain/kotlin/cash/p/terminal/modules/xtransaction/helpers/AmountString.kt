package cash.p.terminal.modules.xtransaction.helpers

import androidx.compose.runtime.Composable
import io.horizontalsystems.core.IAppNumberFormatter
import org.koin.compose.koinInject
import java.math.BigDecimal

@Composable
fun coinAmountString(value: BigDecimal?, coinCode: String, coinDecimals: Int?, sign: String): String {
    if (value == null) return "---"

    return sign + koinInject<IAppNumberFormatter>().formatCoinFull(value, coinCode, coinDecimals ?: 8)
}

@Composable
fun fiatAmountString(value: BigDecimal?, fiatSymbol: String): String {
    if (value == null) return "---"

    return koinInject<IAppNumberFormatter>().formatFiatFull(value, fiatSymbol)
}
