package cash.p.terminal.modules.send.offline

import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun OfflineSignScreenPreview() {
    ComposeAppTheme {
        OfflineSignLayout(
            contentState = OfflineSignContentState(
                summaryState = OfflineSignSummaryState(
                    blockchainName = "Bitcoin",
                    amount = "0.001516 BTC",
                    amountFiat = "$ 1.99",
                    recipient = "bc1qz7xsrl9mw45lj3hvl2t7arcjrs0umyyf69c4en",
                    fee = "0.00000141 BTC",
                ),
                signState = OfflineSignState.Idle,
                selectedFormat = OfflineTransactionFormat.Pcash,
            ),
            onFormatSelect = {},
            onInfoClick = {},
            callbacks = previewOfflineSignCallbacks,
            windowInsets = NavigationBarDefaults.windowInsets,
        )
    }
}

private val previewOfflineSignCallbacks = OfflineSignCallbacks(
    onBackClick = {},
    onCancelClick = {},
    onSignClick = {},
    onSignStateConsumed = {},
    onSigned = {},
)
