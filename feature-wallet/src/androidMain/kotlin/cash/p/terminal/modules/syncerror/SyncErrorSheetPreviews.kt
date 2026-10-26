package cash.p.terminal.modules.syncerror

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.source_blocked_by_provider_error
import cash.p.terminal.ui_compose.annotatedStringResource

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun SyncError403Preview() {
    SyncErrorContent(
        coinCode = "ETH",
        errorDescription = annotatedStringResource(Res.string.source_blocked_by_provider_error),
        showChangeSourceButton = true,
        onClose = {},
        onRetry = {},
        onChangeSource = {},
        onReport = {},
    )
}
