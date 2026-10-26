package cash.p.terminal.modules.offline

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.Coin

@Preview
@Composable
private fun OfflineBlockedBottomSheetPreview() {
    ComposeAppTheme {
        OfflineBlockedBottomSheetContent(
            coinCode = "USDT",
            inProgress = false,
            onGoOnline = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "In progress")
@Composable
private fun OfflineBlockedBottomSheetInProgressPreview() {
    ComposeAppTheme {
        OfflineBlockedBottomSheetContent(
            coinCode = "ETH",
            inProgress = true,
            onGoOnline = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun OfflineModeInfoBottomSheetPreview() {
    ComposeAppTheme {
        OfflineModeInfoBottomSheet(onDismiss = {})
    }
}

private fun previewMember(code: String, badge: String?, balance: String) = OfflineModeAssetItem(
    coin = Coin(uid = code, name = code, code = code),
    badge = badge,
    balance = balance,
)

@Preview(name = "Short list")
@Composable
private fun OfflineModeConfirmationBottomSheetShortPreview() {
    ComposeAppTheme {
        OfflineModeConfirmationBottomSheet(
            blockchainName = "Ethereum",
            isZcash = false,
            members = listOf(
                previewMember("USDT", "ERC20", "1 234.56"),
                previewMember("USDC", "ERC20", "500.00"),
            ),
            inProgress = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Long list")
@Composable
private fun OfflineModeConfirmationBottomSheetLongPreview() {
    ComposeAppTheme {
        OfflineModeConfirmationBottomSheet(
            blockchainName = "BNB Smart Chain",
            isZcash = false,
            members = (1..8).map { previewMember("TOKEN$it", "BEP20", "$it.00") },
            inProgress = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Zcash")
@Composable
private fun OfflineModeConfirmationBottomSheetZcashPreview() {
    ComposeAppTheme {
        OfflineModeConfirmationBottomSheet(
            blockchainName = "Zcash",
            isZcash = true,
            members = listOf(
                previewMember("ZEC", "Transparent", "0.5"),
                previewMember("ZEC", "Unified", "1.2"),
                previewMember("ZEC", "Shielded", "3.4"),
            ),
            inProgress = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
