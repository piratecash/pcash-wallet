package cash.p.terminal.modules.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Suppress("UnusedPrivateMember")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmNewAddressSheetPreview() {
    ComposeAppTheme {
        ConfirmNewAddressSheetContent(
            dontAskAgain = false,
            onDontAskAgainChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
