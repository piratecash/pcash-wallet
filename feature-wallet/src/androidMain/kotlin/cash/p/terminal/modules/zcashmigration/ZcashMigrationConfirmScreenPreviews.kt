package cash.p.terminal.modules.zcashmigration

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.Coin

@Preview
@Composable
private fun ZcashMigrationConfirmScreenPreview() {
    ComposeAppTheme {
        BottomSheetSurface {
            ZcashMigrationConfirmScreen(
                uiState = ZcashMigrationUiState(
                    amount = "1.2345 ZEC",
                    amountFiat = "$123.45",
                    fee = "0.0002 ZEC",
                    feeFiat = "$0.02",
                    migrateEnabled = true,
                    sendResult = null,
                    error = null,
                ),
                coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
                onMigrateClick = {},
                onClose = {},
            )
        }
    }
}
