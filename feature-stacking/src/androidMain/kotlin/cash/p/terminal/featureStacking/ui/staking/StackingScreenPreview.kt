package cash.p.terminal.featureStacking.ui.staking

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.featureStacking.ui.calculatorScreen.CalculatorUIState
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.cosanta
import cash.p.terminal.resources.pirate_cash
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Preview(showBackground = true)
@Composable
private fun StackingScreenPreview() {
    ComposeAppTheme {
        StackingScreen(
            uiState = StackingUIState(
                tabs = listOf(
                    TabItem(stringResource(Res.string.pirate_cash), true, StackingType.PCASH),
                    TabItem(stringResource(Res.string.cosanta), false, StackingType.COSANTA)
                )
            ),
            calculatorUIState = CalculatorUIState(),
            onTabChanged = {},
            onBuyClicked = {},
            onClickClose = {},
            onChartClicked = {},
            onCalculatorValueChanged = { },
            setCalculatorData = { _, _ -> }
        )
    }
}
