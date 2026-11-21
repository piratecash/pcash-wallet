package cash.p.terminal.modules.settings.security.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.settings.security.SecurityCenterCell
import cash.p.terminal.modules.settings.security.tor.SecurityTorSettingsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Alert_Cancel
import cash.p.terminal.resources.Button_Disable
import cash.p.terminal.resources.Button_Enable
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsSecurity_AppRestartWarning
import cash.p.terminal.resources.SettingsSecurity_TorConnectionDescription
import cash.p.terminal.resources.Tor_Alert_Title
import cash.p.terminal.resources.Tor_Connection_Disable
import cash.p.terminal.resources.Tor_Connection_Enable
import cash.p.terminal.resources.Tor_Title
import cash.p.terminal.resources.ic_tor_connection_24
import cash.p.terminal.resources.tor_not_supported_windows
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogSheet
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TorBlock(viewModel: SecurityTorSettingsViewModel, navigation: HSNavigation) {
    LaunchedEffect(viewModel.showRestartAlert) {
        if (viewModel.showRestartAlert) {
            navigation.showTorRestartAlert(viewModel)
            viewModel.restartAppAlertShown()
        }
    }
    val hudMessenger = rememberHudMessenger()
    LaunchedEffect(viewModel.showNotSupported) {
        if (viewModel.showNotSupported) {
            hudMessenger.showWarning(getString(Res.string.tor_not_supported_windows))
            viewModel.notSupportedShown()
        }
    }

    TorSwitchSection(checked = viewModel.torCheckEnabled, onCheckedChange = viewModel::setTorEnabledWithChecks)
}

@Composable
private fun TorSwitchSection(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    CellUniversalLawrenceSection {
        SecurityCenterCell(
            start = {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.ic_tor_connection_24),
                    tint = ComposeAppTheme.colors.iconSecondary,
                    contentDescription = null,
                )
            },
            center = {
                body_leah(
                    text = stringResource(Res.string.Tor_Title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            end = {
                HsSwitch(checked = checked, onCheckedChange = onCheckedChange)
            }
        )
    }

    InfoText(
        text = stringResource(Res.string.SettingsSecurity_TorConnectionDescription),
        paddingBottom = 32.dp
    )
}

private suspend fun HSNavigation.showTorRestartAlert(viewModel: SecurityTorSettingsViewModel) {
    val enabling = viewModel.torCheckEnabled
    slideFromBottom(
        ConfirmationDialogSheet(
            icon = Res.drawable.ic_tor_connection_24,
            title = getString(Res.string.Tor_Alert_Title),
            warningTitle = getString(
                if (enabling) Res.string.Tor_Connection_Enable else Res.string.Tor_Connection_Disable
            ),
            warningText = getString(Res.string.SettingsSecurity_AppRestartWarning),
            actionButtonTitle = getString(if (enabling) Res.string.Button_Enable else Res.string.Button_Disable),
            transparentButtonTitle = getString(Res.string.Alert_Cancel),
            listener = object : ConfirmationDialogSheet.Listener {
                override fun onActionButtonClick() = viewModel.setTorEnabled()
                override fun onTransparentButtonClick() = viewModel.resetSwitch()
                override fun onCancelButtonClick() = viewModel.resetSwitch()
            }
        )
    )
}

@Preview
@Composable
private fun TorSwitchSectionPreview() {
    ComposeAppTheme {
        TorSwitchSection(checked = true, onCheckedChange = {})
    }
}
