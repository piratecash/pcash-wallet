package cash.p.terminal.modules.balance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.balance.BalanceViewItem2
import cash.p.terminal.modules.balance.SyncingProgress
import cash.p.terminal.modules.balance.SyncingProgressType
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.balance.DeemedValue
import java.math.BigDecimal

@Preview(showBackground = true)
@Composable
private fun BalanceCardSwipablePreview() {
    ComposeAppTheme {
        BalanceCard(
            onClick = {},
            onClickSyncError = {},
            viewItem = BalanceViewItem2(
                wallet = WalletFactory.previewWallet(),
                primaryValue = DeemedValue("1.2345678739847587349875938475345345435345345345", false, true),
                secondaryValue = DeemedValue("0.123456 BTC", false, true),
                exchangeValue = DeemedValue("1234.56 USD", false, true),
                fullDiff = "+5.67%",
                diff = BigDecimal("5.67"),
                displayDiffOptionType = DisplayDiffOptionType.BOTH,
                syncingProgress = SyncingProgress(
                    type = SyncingProgressType.ProgressWithRing,
                    progress = 10.0
                ),
                syncingTextValue = null,
                syncedUntilTextValue = null,
                failedIconVisible = false,
                badge = "HOT",
                stackingUnpaid = DeemedValue("12.34 BTC", false, false),
                errorMessage = null,
                isWatchAccount = false,
                isSwipeToDeleteEnabled = false
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BalanceCardOfflinePreview() {
    ComposeAppTheme {
        BalanceCard(
            onClick = {},
            onClickSyncError = {},
            viewItem = BalanceViewItem2(
                wallet = WalletFactory.previewWallet(),
                primaryValue = DeemedValue("1.23456", false, true),
                secondaryValue = DeemedValue("0.123456 BTC", false, true),
                exchangeValue = DeemedValue("1234.56 USD", false, true),
                fullDiff = "+5.67%",
                diff = BigDecimal("5.67"),
                displayDiffOptionType = DisplayDiffOptionType.BOTH,
                syncingProgress = SyncingProgress(null, null),
                syncingTextValue = null,
                syncedUntilTextValue = null,
                failedIconVisible = false,
                badge = "HOT",
                stackingUnpaid = null,
                errorMessage = null,
                isWatchAccount = false,
                isSwipeToDeleteEnabled = false,
                offline = true,
            )
        )
    }
}
