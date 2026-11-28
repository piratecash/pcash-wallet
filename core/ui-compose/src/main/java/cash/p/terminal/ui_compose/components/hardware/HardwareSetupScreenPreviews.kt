package cash.p.terminal.ui_compose.components.hardware

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun HardwareSetupScreenConnectPreview() {
    HardwareSetupPreview(loading = false)
}

@Preview(name = "Opening app")
@Composable
private fun HardwareSetupScreenProgressPreview() {
    HardwareSetupPreview(loading = true, statusText = "2 of 3: confirm opening the Ethereum app on Ledger")
}

@Preview(name = "Error")
@Composable
private fun HardwareSetupScreenErrorPreview() {
    HardwareSetupPreview(loading = false, errorText = "Another Ledger or passphrase is connected")
}

@Composable
private fun HardwareSetupPreview(loading: Boolean, statusText: String? = null, errorText: String? = null) {
    ComposeAppTheme {
        HardwareSetupScreen(
            title = "Ledger Wallet",
            description = "Connect your Ledger with a USB cable and unlock it.",
            connectButtonTitle = "Connect Ledger",
            loading = loading,
            onConnect = {},
            onBack = {},
            statusText = statusText,
            errorText = errorText,
        )
    }
}
