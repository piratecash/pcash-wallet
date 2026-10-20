package cash.p.terminal.modules.manageaccount

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.balance.HeaderNote
import cash.p.terminal.modules.manageaccount.ManageAccountModule.KeyAction
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType

@Preview(showBackground = true)
@Composable
private fun ManageAccountScreenPreview() {
    ComposeAppTheme {
        ManageAccountScreen(
            navigation = HSNavigation(NavBackStack()),
            viewState = ManageAccountModule.ViewState(
                title = "Account name",
                newName = "Account name",
                canSave = true,
                closeScreen = false,
                headerNote = HeaderNote.None,
                keyActions = listOf(
                    KeyAction.AccessCodeRecovery,
                    KeyAction.ChangeAccessCode,
                    KeyAction.ResetToFactorySettings,
                    KeyAction.ForgotAccessCode,
                ),
                backupActions = emptyList(),
                signedHashes = 2,
                canBeDuplicated = true
            ),
            account = Account(
                id = "id",
                name = "name",
                type = AccountType.HardwareCard(
                    cardId = "",
                    backupCardsCount = 0,
                    walletPublicKey = "",
                    signedHashes = 2
                ),
                origin = AccountOrigin.Created,
                level = 0
            ),
            onCloseClicked = {},
            onSaveClicked = {},
            onNameChanged = {},
            onActionClick = {}
        )
    }
}
