package cash.p.terminal.modules.manageaccount.backupkey

import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.openFaq
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.modules.manageaccount.ui.PassphraseCell
import cash.p.terminal.modules.manageaccount.ui.SeedPhraseList
import cash.p.terminal.modules.manageaccount.backupconfirmkey.BackupConfirmKeyPage
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Info_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_info_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.RecoveryPhrase_Description
import cash.p.terminal.resources.RecoveryPhrase_Title
import cash.p.terminal.resources.RecoveryPhrase_Verify
import cash.p.terminal.resources.error_account_not_found

class BackupKeyPage(val input: BackupKeyInput) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        RecoveryPhraseScreen(navigation, input.accountId)
    }
}

@Composable
fun RecoveryPhraseScreen(
    navigation: HSNavigation,
    accountId: String
) {
    val viewModel = koinViewModel<BackupKeyViewModel> { parametersOf(accountId) }
    val account = viewModel.account
    val hudMessenger = rememberHudMessenger()

    LaunchedEffect(viewModel.accountNotFound, account) {
        if (viewModel.accountNotFound || account == null) {
            hudMessenger.showError(getString(Res.string.error_account_not_found))
            navigation.navigateUp()
        }
    }

    if (viewModel.accountNotFound || account == null) return

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.RecoveryPhrase_Title),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Info_Title),
                        icon = Res.drawable.ic_info_24,
                        onClick = {
                            navigation.openFaq(FaqTopic.PrivateKeys)
                        }
                    ),
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = {
                            navigation.navigateUpSafely()
                        }
                    )
                )
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            var hidden by remember { mutableStateOf(true) }

            InfoText(text = stringResource(Res.string.RecoveryPhrase_Description))
            Spacer(Modifier.height(12.dp))
            SeedPhraseList(
                wordsNumbered = viewModel.wordsNumbered,
                hidden = hidden
            ) {
                hidden = !hidden
            }
            if (viewModel.showPassphraseBlock) {
                Spacer(Modifier.height(24.dp))
                PassphraseCell(viewModel.passphrase, hidden)
            }
            Spacer(modifier = Modifier.weight(1f))
            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    title = stringResource(Res.string.RecoveryPhrase_Verify),
                    onClick = {
                        navigation.slideFromRight(BackupConfirmKeyPage(account))
                    },
                )
            }
        }
    }
}
