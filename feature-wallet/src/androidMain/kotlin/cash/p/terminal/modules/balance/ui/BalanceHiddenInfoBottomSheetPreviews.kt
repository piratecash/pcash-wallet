package cash.p.terminal.modules.balance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun BalanceHiddenInfoContentPreview() {
    ComposeAppTheme {
        BalanceHiddenInfoContent(
            onGotIt = {},
            onDontShowAgain = {},
            onClose = {},
        )
    }
}
