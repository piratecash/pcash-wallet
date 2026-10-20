package cash.p.terminal.modules.backuplocal.password

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.premiumAction
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellowWithSpinner
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.FormsInputPassword
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.resources.ConfirmPassphrase
import cash.p.terminal.resources.LocalBackup_BackupSaved
import cash.p.terminal.resources.LocalBackup_DontForgetPasswordWarning
import cash.p.terminal.resources.LocalBackup_ProtextBackupWithPasswordInfo
import cash.p.terminal.resources.LocalBackup_SaveAndBackup
import cash.p.terminal.resources.LocalBackup_SetPassword
import cash.p.terminal.resources.Password
import cash.p.terminal.resources.ic_download_24
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.local_backup_configure_duress_first
import cash.p.terminal.resources.local_backup_confirm_duress_password
import cash.p.terminal.resources.local_backup_duress_info_description
import cash.p.terminal.resources.local_backup_duress_info_title
import cash.p.terminal.resources.local_backup_duress_password
import cash.p.terminal.resources.local_backup_duress_toggle
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun LocalBackupPasswordScreen(
    backupType: BackupType,
    navigation: HSNavigation,
    onBackClick: () -> Unit,
    onFinish: () -> Unit
) {
    val viewModel = koinViewModel<BackupLocalPasswordViewModel> { parametersOf(backupType) }

    val hudMessenger = rememberHudMessenger()
    var showDuressInfoSheet by remember { mutableStateOf(false) }
    val uiState = viewModel.uiState
    val backupSavedText = stringResource(Res.string.LocalBackup_BackupSaved)
    val configureDuressFirstText = stringResource(Res.string.local_backup_configure_duress_first)

    // Show duress toggle only for full backup (single wallet = one password)
    val showDuressToggle = backupType is BackupType.FullBackup

    val saveBackupFile = koinInject<BackupFileLauncher>().rememberSaveBackupFile(
        bytes = uiState.backupData,
        onSave = {
            hudMessenger.showSuccess(backupSavedText, Res.drawable.ic_download_24)
            viewModel.backupFinished()
        },
        onCancel = viewModel::backupCanceled,
        onError = { e -> hudMessenger.showError(e.message ?: e::class.simpleName.orEmpty()) },
    )

    uiState.error?.let { error ->
        hudMessenger.showError(error)
        onFinish()
        viewModel.accountErrorIsShown()
    }

    LaunchedEffect(uiState.backupData) {
        if (uiState.backupData != null) saveBackupFile(viewModel.backupFileName)
    }

    if (uiState.closeScreen) {
        viewModel.closeScreenCalled()
        onFinish()
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.LocalBackup_SetPassword),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                }
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {

                InfoText(text = stringResource(Res.string.LocalBackup_ProtextBackupWithPasswordInfo))
                VSpacer(24.dp)
                FormsInputPassword(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    hint = stringResource(Res.string.Password),
                    state = uiState.passphraseState,
                    onValueChange = viewModel::onChangePassphrase,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                VSpacer(16.dp)
                FormsInputPassword(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    hint = stringResource(Res.string.ConfirmPassphrase),
                    state = uiState.passphraseConfirmState,
                    onValueChange = viewModel::onChangePassphraseConfirmation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                // Duress backup toggle (only for full backup)
                if (showDuressToggle) {
                    VSpacer(24.dp)
                    DuressBackupToggle(
                        enabled = uiState.duressBackupEnabled,
                        available = uiState.duressBackupAvailable && uiState.pinEnabled,
                        onInfoClick = { showDuressInfoSheet = true },
                        onToggleClick = {
                            // Check premium and then verify PIN
                            navigation.premiumAction {
                                viewModel.onDuressBackupToggle(true)
                            }
                        },
                        onToggleOff = {
                            viewModel.onDuressBackupToggle(false)
                        },
                        onDisabledClick = {
                            // Show message that duress mode needs to be configured
                            hudMessenger.showError(configureDuressFirstText)
                        }
                    )

                    // Duress password fields when enabled
                    if (uiState.duressBackupEnabled) {
                        VSpacer(24.dp)
                        FormsInputPassword(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            hint = stringResource(Res.string.local_backup_duress_password),
                            state = uiState.duressPassphraseState,
                            onValueChange = viewModel::onChangeDuressPassphrase,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        VSpacer(16.dp)
                        FormsInputPassword(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            hint = stringResource(Res.string.local_backup_confirm_duress_password),
                            state = uiState.duressPassphraseConfirmState,
                            onValueChange = viewModel::onChangeDuressPassphraseConfirmation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                    }
                }

                VSpacer(32.dp)
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(Res.string.LocalBackup_DontForgetPasswordWarning)
                )
                VSpacer(32.dp)

            }

            // Info bottom sheet for duress backup
            if (showDuressInfoSheet) {
                InfoBottomSheet(
                    title = stringResource(Res.string.local_backup_duress_info_title),
                    text = stringResource(Res.string.local_backup_duress_info_description),
                    onDismiss = { showDuressInfoSheet = false }
                )
            }
            ButtonsGroupWithShade {
                ButtonPrimaryYellowWithSpinner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    title = stringResource(Res.string.LocalBackup_SaveAndBackup),
                    showSpinner = uiState.showButtonSpinner,
                    enabled = uiState.showButtonSpinner.not(),
                    onClick = {
                        viewModel.onSaveClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun DuressBackupToggle(
    enabled: Boolean,
    available: Boolean,
    onInfoClick: () -> Unit,
    onToggleClick: () -> Unit,
    onToggleOff: () -> Unit,
    onDisabledClick: () -> Unit
) {
    CellUniversalLawrenceSection(listOf {
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = {
                if (!available) {
                    onDisabledClick()
                }
            },
        ) {
            body_leah(
                text = stringResource(Res.string.local_backup_duress_toggle),
            )
            HsIconButton(
                onClick = onInfoClick,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary
                )
            }
            HFillSpacer(minWidth = 4.dp)
            HsSwitch(
                checked = enabled,
                onCheckedChange = { isChecked ->
                    if (!available) {
                        onDisabledClick()
                        return@HsSwitch
                    }
                    if (isChecked) {
                        onToggleClick()
                    } else {
                        onToggleOff()
                    }
                }
            )
        }
    })
}
