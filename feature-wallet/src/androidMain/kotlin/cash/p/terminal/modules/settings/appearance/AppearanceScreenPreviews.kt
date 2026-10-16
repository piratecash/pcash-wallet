package cash.p.terminal.modules.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.balance.BalanceViewType

@Preview
@Composable
private fun AppearanceScreenPreview() {
    ComposeAppTheme {
        AppearanceScreen(
            uiState = AppearanceUIState(
                launchScreenOptions = Select(LaunchPage.Auto, LaunchPage.entries),
                themeOptions = Select(ThemeType.Dark, ThemeType.entries),
                balanceViewTypeOptions = Select(BalanceViewType.CoinThenFiat, BalanceViewType.entries),
                marketsTabHidden = false,
                balanceTabButtonsHidden = false,
                selectedTheme = ThemeType.Dark,
                selectedLaunchScreen = LaunchPage.Auto,
                selectedBalanceViewType = BalanceViewType.CoinThenFiat,
                priceChangeInterval = PriceChangeInterval.LAST_24H,
                priceChangeIntervalOptions = Select(PriceChangeInterval.LAST_24H, PriceChangeInterval.entries),
            ),
            actions = AppearanceActions({}, {}, {}, {}, {}, {}),
            onClose = {},
            onAppIconClick = {}
        )
    }
}
