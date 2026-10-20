package cash.p.terminal.modules.restoreaccount.duplicatewallet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun DuplicateWalletScreenPreview() {
    ComposeAppTheme {
        DuplicateWalletScreen(
            uiState = DuplicateWalletUiState(
                accountName = "My Wallet",
                passphraseEnabled = true,
                passphraseAvailable = true,
                passcodeOld = "",
            ),
            passphraseTermsAccepted = true,
            onEnterName = {},
            onTogglePassphrase = {},
            onChangePassphrase = {},
            onChangePassphraseConfirmation = {},
            onCreate = {},
            onBackClick = {},
            onOpenTerms = {},
            onFinish = {}
        )
    }
}
