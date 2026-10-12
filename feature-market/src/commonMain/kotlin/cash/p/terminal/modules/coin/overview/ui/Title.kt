package cash.p.terminal.modules.coin.overview.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.TabBalance
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.IAppNumberFormatter
import org.koin.compose.koinInject
import java.math.BigDecimal

@Composable
fun Title(rate: String?, rateDiff: BigDecimal?) {
    val numberFormatter = koinInject<IAppNumberFormatter>()
    TabBalance(borderTop = true) {
        Text(
            modifier = Modifier.padding(end = 8.dp),
            text = rate ?: "",
            style = ComposeAppTheme.typography.headline1,
            color = ComposeAppTheme.colors.textPrimary
        )

        rateDiff?.let { value ->
            val sign = if (value >= BigDecimal.ZERO) "+" else "-"
            val text = numberFormatter.format(value.abs(), 0, 2, sign, "%")

            val color = when {
                value >= BigDecimal.ZERO -> ComposeAppTheme.colors.statusSuccess
                else -> ComposeAppTheme.colors.statusError
            }

            Text(
                text = text,
                style = ComposeAppTheme.typography.subhead1,
                color = color
            )
        }
    }
}
