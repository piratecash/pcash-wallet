package cash.p.terminal.modules.unlinkaccount

import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.manageaccount.AccountDeletionError
import cash.p.terminal.modules.manageaccount.ManageAccountPage
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import kotlinx.coroutines.delay
import cash.p.terminal.resources.Hud_Text_Done
import cash.p.terminal.resources.ManageKeys_Delete_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_red_24

internal class UnlinkAccountSheet(val input: Account) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ComposeAppTheme {
            UnlinkAccountScreen(navigation, input)
        }
    }
}

@Composable
private fun UnlinkAccountScreen(navigation: HSNavigation, account: Account) {
    val viewModel =
        koinViewModel<UnlinkAccountViewModel> { parametersOf(account) }

    val confirmations = viewModel.confirmations
    val unlinkEnabled = viewModel.unlinkEnabled
    val deleteWarningMessages = listOfNotNull(viewModel.deleteWarningMsg)
    val hudMessenger = rememberHudMessenger()
    AccountDeletionError(viewModel.deletionState)

    LaunchedEffect(viewModel.closeScreen) {
        if (viewModel.closeScreen) {
            hudMessenger.showSuccess(getString(Res.string.Hud_Text_Done))
            delay(1000)
            navigation.removeLastUntil(ManageAccountPage::class, inclusive = true)
        }
    }

    BottomSheetHeader(
        iconPainter = painterResource(Res.drawable.ic_attention_red_24),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusError),
        title = stringResource(Res.string.ManageKeys_Delete_Title),
        onCloseClick = {
            navigation.navigateUpSafely()
        }
    ) {

        Spacer(Modifier.height(12.dp))
        CellUniversalLawrenceSection(confirmations, showFrame = true) { item ->
            RowUniversal(
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { viewModel.toggleConfirm(item) }
            ) {
                HsCheckbox(
                    checked = item.confirmed,
                    onCheckedChange = {
                        viewModel.toggleConfirm(item)
                    },
                )
                Spacer(Modifier.width(16.dp))
                subhead2_leah(
                    text = item.confirmationType.title.getString(),
                )
            }
        }

        deleteWarningMessages.forEach {
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(it)
            )
        }

        Spacer(Modifier.height(32.dp))
        ButtonPrimaryRed(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            title = stringResource(viewModel.deleteButtonText),
            onClick = {
                viewModel.onUnlink()
            },
            enabled = unlinkEnabled
        )
        Spacer(Modifier.height(32.dp))
    }
}
