package cash.p.terminal.modules.backupalert

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.wallet.navigation.WalletPages
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyPage
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.BackupButtons
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.BackupRecoveryPhrase_Description
import cash.p.terminal.resources.BackupRecoveryPhrase_Later
import cash.p.terminal.resources.BackupRecoveryPhrase_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_24

class BackupRecoveryPhraseSheet(val input: Account) : HSBottomSheet() {

    /** The opener authorizes it: a sheet-owned launcher is dropped when the PIN page replaces the sheet. */
    data class ShamirBackupRequested(val accountId: String)

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        BackupRecoveryPhraseScreen(
            navigation = navigation,
            account = input,
            onShamirBackupClick = if (input.type is AccountType.Slip39) {
                {
                    navigation.setResult(this@BackupRecoveryPhraseSheet, ShamirBackupRequested(input.id))
                    navigation.navigateUpSafely()
                }
            } else {
                null
            }
        )
    }
}

@Composable
fun BackupRecoveryPhraseScreen(
    navigation: HSNavigation,
    account: Account,
    onShamirBackupClick: (() -> Unit)?
) {
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_attention_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
            title = stringResource(Res.string.BackupRecoveryPhrase_Title),
            onCloseClick = {
                navigation.navigateUpSafely()
            }
        ) {
            VSpacer(12.dp)
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.BackupRecoveryPhrase_Description)
            )

            VSpacer(32.dp)
            BackupButtons(
                onManualBackupClick = {
                    navigation.slideFromBottom(
                        BackupKeyPage(BackupKeyInput(account.id))
                    )
                },
                onLocalBackupClick = {
                    navigation.slideFromBottom(getKoinInstance<WalletPages>().backupLocal(account))
                },
                onShamirBackupClick = onShamirBackupClick,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.BackupRecoveryPhrase_Later),
                onClick = {
                    navigation.navigateUpSafely()
                }
            )
            VSpacer(32.dp)
        }
    }
}
