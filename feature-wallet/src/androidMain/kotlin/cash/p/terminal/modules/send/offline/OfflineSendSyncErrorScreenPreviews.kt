package cash.p.terminal.modules.send.offline

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(name = "No Connection")
@Composable
private fun OfflineSendSyncErrorScreenNoConnectionPreview() {
    ComposeAppTheme {
        OfflineSendSyncErrorScreen(
            state = OfflineSendSyncErrorState(
                title = "Send PIRATE",
                coinCode = "PIRATE",
                noConnection = true,
                inProgress = false,
                sourceChangeable = false,
            ),
            callbacks = previewOfflineSendSyncErrorCallbacks,
        )
    }
}

@Preview(name = "Sync Error")
@Composable
private fun OfflineSendSyncErrorScreenProviderErrorPreview() {
    ComposeAppTheme {
        OfflineSendSyncErrorScreen(
            state = OfflineSendSyncErrorState(
                title = "Send PIRATE",
                coinCode = "PIRATE",
                noConnection = false,
                inProgress = false,
                sourceChangeable = true,
            ),
            callbacks = previewOfflineSendSyncErrorCallbacks,
        )
    }
}

@Preview(name = "Progress")
@Composable
private fun OfflineSendSyncErrorScreenProgressPreview() {
    ComposeAppTheme {
        OfflineSendSyncErrorScreen(
            state = OfflineSendSyncErrorState(
                title = "Send PIRATE",
                coinCode = "PIRATE",
                noConnection = true,
                inProgress = true,
                sourceChangeable = false,
            ),
            callbacks = previewOfflineSendSyncErrorCallbacks,
        )
    }
}

private val previewOfflineSendSyncErrorCallbacks = OfflineSendSyncErrorCallbacks(
    onBackClick = {},
    onRetryClick = {},
    onChangeSourceClick = {},
    onSignOfflineClick = {},
)
