package cash.p.terminal.modules.manageaccount.zcashkeys

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun ZcashKeysScreenPreview() {
    ComposeAppTheme {
        ZcashKeysScreen(
            uiState = ZcashKeysUiState(available = ZcashPrivateKeyType.entries),
            onReveal = {},
            onShowKey = {},
            onDismissError = {},
            onClose = {},
        )
    }
}
