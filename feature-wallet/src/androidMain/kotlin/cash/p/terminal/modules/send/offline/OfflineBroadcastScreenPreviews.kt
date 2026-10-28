package cash.p.terminal.modules.send.offline

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastConfirmPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Confirm,
                confirm = OfflineBroadcastConfirm(
                    rawHex = "0200000000010112ab34cd56ef7890",
                    selectable = true,
                    blockchainName = "Binance Smart Chain",
                ),
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = null,
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastEnableNetworkPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Confirm,
                confirm = OfflineBroadcastConfirm(
                    rawHex = "0200000000010112ab34cd56ef7890",
                    selectable = false,
                    blockchainName = "Bitcoin",
                    action = OfflineBroadcastConfirmAction.EnableNetwork("Bitcoin"),
                ),
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = null,
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastPreparingNetworkPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Confirm,
                confirm = OfflineBroadcastConfirm(
                    rawHex = "0200000000010112ab34cd56ef7890",
                    selectable = false,
                    blockchainName = "Bitcoin",
                    action = OfflineBroadcastConfirmAction.PreparingNetwork("Bitcoin"),
                ),
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = null,
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastSuccessErrorPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Result,
                confirm = null,
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = OfflineBroadcastResult.Error(
                    networkName = "Binance Smart Chain",
                    rawHex = "0200000000010112ab34cd56ef7890",
                    message = "Failed to broadcast transaction. Please try again.",
                ),
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastSuccessPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Result,
                confirm = null,
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = OfflineBroadcastResult.Success(
                    networkName = "Binance Smart Chain",
                    txHash = "0MXgWabcdefghijklmnopL7KCS",
                    queued = false,
                    explorerUrl = "https://bscscan.com/tx/0x",
                ),
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OfflineBroadcastQueuedPreview() {
    ComposeAppTheme {
        OfflineBroadcastScreen(
            uiState = OfflineBroadcastUiState(
                step = OfflineBroadcastStep.Result,
                confirm = null,
                selectableBlockchains = emptyList(),
                selectedBlockchain = null,
                broadcasting = false,
                result = OfflineBroadcastResult.Success(
                    networkName = "Bitcoin",
                    txHash = "7f8a9b0c1d2e3f405162738495a6b7c8d9e0f11223344556677889900aabbccd",
                    queued = true,
                    explorerUrl = "https://blockchair.com/bitcoin/transaction/",
                ),
                dismissError = null,
                errorMessage = null,
            ),
            onPickNetwork = {},
            onSelectBlockchain = {},
            onPrimaryAction = {},
            onRetry = {},
            onBack = {},
            onClose = {},
        )
    }
}
