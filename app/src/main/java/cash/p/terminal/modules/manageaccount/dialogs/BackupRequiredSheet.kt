package cash.p.terminal.modules.manageaccount.dialogs

import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.backuplocal.BackupLocalPage
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyPage
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.BackupButtons
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.wallet.Account
import kotlinx.parcelize.Parcelize
import cash.p.terminal.navigation.navigateUpSafely

class BackupRequiredSheet(val input: Input) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        BackupRequiredScreen(navigation, input.account, input.text)
    }

    @Parcelize
    data class Input(val account: Account, val text: String) : Parcelable
}

@Composable
fun BackupRequiredScreen(navigation: HSNavigation, account: Account, text: String) {
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_attention_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            title = stringResource(R.string.ManageAccount_BackupRequired_Title),
            onCloseClick = {
                navigation.navigateUpSafely()
            }
        ) {
            VSpacer(12.dp)
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = text
            )
            VSpacer(32.dp)
            BackupButtons(
                onManualBackupClick = {
                    navigation.slideFromBottom(
                        BackupKeyPage(BackupKeyInput(account.id))
                    )
                },
                onLocalBackupClick = {
                    navigation.slideFromBottom(BackupLocalPage(account))
                },
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.BackupRecoveryPhrase_Later),
                onClick = {
                    navigation.navigateUpSafely()
                }
            )
            VSpacer(32.dp)
        }
    }
}
