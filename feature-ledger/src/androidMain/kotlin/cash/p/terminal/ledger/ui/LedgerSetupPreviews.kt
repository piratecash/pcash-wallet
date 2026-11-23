package cash.p.terminal.ledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ledger.domain.LedgerAppProgress
import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(name = "USB permission")
@Composable
private fun LedgerSetupPermissionPreview() {
    LedgerSetupPreview(LedgerSetupUiState(loading = true, awaitingPermission = true))
}

@Preview(name = "Opening app")
@Composable
private fun LedgerSetupProgressPreview() {
    LedgerSetupPreview(LedgerSetupUiState(loading = true, progress = LedgerAppProgress(2, 3, "Ethereum")))
}

@Preview(name = "Skipped apps")
@Composable
private fun LedgerSetupSkippedPreview() {
    LedgerSetupPreview(LedgerSetupUiState(skippedApps = listOf("Bitcoin", "Monero")))
}

@Preview(name = "Verify address error")
@Composable
private fun LedgerVerifyAddressErrorPreview() {
    ComposeAppTheme {
        LedgerVerifyAddressSection(
            verifying = false,
            error = LedgerIdentityMismatchException(),
            enabled = true,
            onVerify = {},
        )
    }
}

@Composable
private fun LedgerSetupPreview(uiState: LedgerSetupUiState) {
    ComposeAppTheme {
        LedgerSetupScreen(uiState = uiState, onConnect = {}, onDismissSkippedApps = {}, onBack = {})
    }
}
