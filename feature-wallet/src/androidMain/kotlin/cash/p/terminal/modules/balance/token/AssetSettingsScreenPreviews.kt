package cash.p.terminal.modules.balance.token

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.modules.offline.OfflineModeToggleUiState
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun AssetSettingsScreenPreview() {
    ComposeAppTheme {
        AssetSettingsScreen(
            amlCheckEnabled = true,
            onAmlCheckChange = {},
            pricePeriod = DisplayPricePeriod.ONE_DAY,
            displayDiffOptionType = DisplayDiffOptionType.BOTH,
            isRoundingAmount = false,
            onPricePeriodChange = {},
            onDisplayDiffOptionTypeChange = {},
            onRoundingAmountChange = {},
            onAddressPoisoningViewClick = {},
            transactionFiltersEnabled = false,
            onTransactionFiltersChange = {},
            offlineUiState = OfflineModeToggleUiState(description = "Ethereum, USDT"),
            onConfirmOffline = {},
            onGoOnline = {},
            onOfflineSheetDismiss = {},
            navigation = HSNavigation(NavBackStack()),
            onBack = {},
            creationBlockVisible = true,
            currentHeightText = "2477000",
            onCreationBlockClick = {},
        )
    }
}
