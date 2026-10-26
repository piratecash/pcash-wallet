package cash.p.terminal.featureStacking.ui.calculatorScreen

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.network.pirate.domain.enity.PeriodType
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(
    showBackground = true,
    backgroundColor = 0xFF888888,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    showBackground = true,
    backgroundColor = 0,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun CalculatorScreenPreview() {
    ComposeAppTheme {
        CalculatorScreen(
            uiState = CalculatorUIState(
                amount = "100",
                coinExchange = "1 PCASH = 0.105821 USD",
                calculateResult = listOf(
                    CalculatorItem(
                        period = PeriodType.DAY,
                        amount = "+10000",
                        amountSecondary = "+1058.21$"
                    ),
                    CalculatorItem(
                        period = PeriodType.WEEK,
                        amount = "+123000",
                        amountSecondary = "+134058.21$"
                    ),
                    CalculatorItem(
                        period = PeriodType.MONTH,
                        amount = "+13423000",
                        amountSecondary = "+16734058.21$"
                    )
                ),
                coinSecondary = "USD"
            ),
            onDoneClicked = {},
            onValueChanged = {}
        )
    }
}
