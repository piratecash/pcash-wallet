package cash.p.terminal.modules.declinedtokens

import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens
import cash.p.terminal.core.managers.DeclinedToken
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun DeclinedTokensInfoBottomSheetPreview() {
    ComposeAppTheme {
        DeclinedTokensInfoBottomSheet(
            wallets = listOf(previewWallet(3)),
            onReview = {},
            onAddAll = {},
            onSkipAll = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensInfoBottomSheetTwoWalletsPreview() {
    ComposeAppTheme {
        DeclinedTokensInfoBottomSheet(
            wallets = listOf(previewWallet(2), previewWallet(3, accountName = "Second Wallet")),
            onReview = {},
            onAddAll = {},
            onSkipAll = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensInfoBottomSheetLongListPreview() {
    ComposeAppTheme {
        DeclinedTokensInfoBottomSheet(
            wallets = listOf(previewWallet(8)),
            onReview = {},
            onAddAll = {},
            onSkipAll = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensSelectBottomSheetPreview() {
    ComposeAppTheme {
        DeclinedTokensSelectBottomSheet(
            wallets = listOf(previewWallet(4)),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensSelectBottomSheetTwoWalletsPreview() {
    ComposeAppTheme {
        DeclinedTokensSelectBottomSheet(
            wallets = listOf(previewWallet(2), previewWallet(3, accountName = "Second Wallet")),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun DeclinedTokensSheetsPreview() {
    ComposeAppTheme {
        DeclinedTokensSheets(
            review = DeclinedTokensReview(
                listOf(previewWallet(2), previewWallet(3, accountName = "Second Wallet"))
            ),
            onReview = {},
            onApprove = {},
            onDismiss = {},
        )
    }
}

private fun previewWallet(tokenCount: Int, accountName: String = "Main Wallet"): WalletDeclinedTokens =
    WalletDeclinedTokens(
        accountId = accountName,
        accountName = accountName,
        tokens = (1..tokenCount).map { index ->
            DeclinedToken(tokenQueryId = "token-$index", coinName = "Token $index", coinCode = "TK$index")
        },
    )
