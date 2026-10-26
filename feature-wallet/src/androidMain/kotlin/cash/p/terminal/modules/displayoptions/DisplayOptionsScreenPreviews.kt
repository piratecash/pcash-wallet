package cash.p.terminal.modules.displayoptions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(showBackground = true)
@Composable
private fun DisplayOptionsScreenPreview() {
    ComposeAppTheme {
        DisplayOptionsScreen(
            navigation = HSNavigation(NavBackStack()),
            uiState = DisplayOptionsUiState(
                isCoinManagerEnabled = true,
                isRoundingAmountMainPage = true,
                pricePeriod = DisplayPricePeriod.ONE_DAY,
                displayDiffOptionType = DisplayDiffOptionType.BOTH
            ),
            onPricePeriodChanged = {},
            onPercentChangeToggled = {},
            onPriceChangeToggled = {},
            onRoundingAmountMainPageToggled = {}
        )
    }
}
