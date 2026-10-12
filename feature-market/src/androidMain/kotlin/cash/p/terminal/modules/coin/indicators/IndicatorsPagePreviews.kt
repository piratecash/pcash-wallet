package cash.p.terminal.modules.coin.indicators

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun IndicatorsPreview() {
    ComposeAppTheme {
        IndicatorsScreen(HSNavigation(NavBackStack()))
    }
}
