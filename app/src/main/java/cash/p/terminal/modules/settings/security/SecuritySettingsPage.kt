package cash.p.terminal.modules.settings.security

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.ui.compose.components.NewDot
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.core.fullRestart
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.EditPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.modules.settings.displaytransactions.DisplayTransactionsPage
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsUiState
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
import cash.p.terminal.modules.settings.security.tor.SecurityTorSettingsModule
import cash.p.terminal.modules.settings.security.tor.SecurityTorSettingsViewModel
import cash.p.terminal.modules.settings.security.ui.HardwareWalletBiometricBlock
import cash.p.terminal.modules.settings.security.ui.PasscodeBlock
import cash.p.terminal.modules.settings.security.ui.SystemPinBlock
import cash.p.terminal.modules.settings.security.ui.TorBlock
import cash.p.terminal.modules.settings.security.ui.TransactionAutoHideBlock
import cash.p.terminal.modules.settings.security.ui.TransferPasscodeBlock
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.ui_compose.components.HsSwitch
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogSheet
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.HudHelper
import io.horizontalsystems.core.ui.dialogs.ChecklistConfirmationSheet
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.navigation.navigateUpSafely

class SecuritySettingsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val torViewModel = viewModel<SecurityTorSettingsViewModel>(
            factory = SecurityTorSettingsModule.Factory()
        )
        val securitySettingsViewModel = koinViewModel<SecuritySettingsViewModel>()
        val context = LocalContext.current
        val activity = LocalActivity.current

        SecurityCenterScreen(
            securitySettingsViewModel = securitySettingsViewModel,
            uiState = securitySettingsViewModel.uiState,
            torViewModel = torViewModel,
            navigation = navigation,
            onTransactionAutoHideEnabledChange = { enabled ->
                if (enabled) {
                    if (securitySettingsViewModel.uiState.pinEnabled) {
                        securitySettingsViewModel.onTransactionAutoHideEnabledChange(true)
                    } else {
                        navigation.ensurePinSet(R.string.PinSet_Title) {
                            securitySettingsViewModel.onTransactionAutoHideEnabledChange(true)
                        }
                    }
                } else {
                    navigation.authorizedAction(
                        ConfirmPinPage.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                            pinType = PinType.TRANSACTIONS_HIDE
                        )
                    ) {
                        securitySettingsViewModel.onTransactionAutoHideEnabledChange(false)
                    }
                }
            },
            onChangeDisplayClicked = {
                navigation.authorizedAction(
                    ConfirmPinPage.InputConfirm(
                        descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                        pinType = PinType.TRANSACTIONS_HIDE
                    )
                ) {
                    navigation.slideFromRight(DisplayTransactionsPage())
                }
            },
            onSetTransactionAutoHidePinClicked = {
                if (!securitySettingsViewModel.uiState.transactionAutoHideSeparatePinExists) {
                    navigation.authorizedAction(
                        ConfirmPinPage.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode,
                            pinType = PinType.REGULAR
                        )
                    ) {
                        navigation.slideFromRight(
                            SetPinPage(
                                SetPinPage.Input(
                                    descriptionResId = R.string.PinSet_Transactions_Hide,
                                    pinType = PinType.TRANSACTIONS_HIDE
                                )
                            )
                        )
                    }
                } else {
                    navigation.authorizedAction(
                        ConfirmPinPage.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                            pinType = PinType.TRANSACTIONS_HIDE
                        )
                    ) {
                        navigation.slideFromRight(
                            EditPinPage(
                                SetPinPage.Input(
                                    R.string.PinSet_Transactions_Hide,
                                    PinType.TRANSACTIONS_HIDE
                                )
                            )
                        )
                    }
                }
            },
            onDisableTransactionAutoHidePinClicked = {
                navigation.authorizedAction(
                    ConfirmPinPage.InputConfirm(
                        descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                        pinType = PinType.TRANSACTIONS_HIDE
                    )
                ) {
                    securitySettingsViewModel.onDisableTransactionAutoHidePin()
                }
            },
            showAppRestartAlert = { navigation.showAppRestartAlert(context, torViewModel) },
            restartApp = { activity?.fullRestart() },
            onTransferPasscodeEnabledChange = { enabled ->
                if (enabled) {
                    if (securitySettingsViewModel.uiState.pinEnabled) {
                        securitySettingsViewModel.onTransferPasscodeEnabledChange(true)
                    } else {
                        navigation.ensurePinSet(R.string.PinSet_Title) {
                            securitySettingsViewModel.onTransferPasscodeEnabledChange(true)
                        }
                    }
                } else {
                    navigation.authorizedAction(
                        ConfirmPinPage.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode_Transfer,
                            pinType = PinType.REGULAR
                        )
                    ) {
                        securitySettingsViewModel.onTransferPasscodeEnabledChange(false)
                    }
                }
            },
            onEnableSaveAccessCodeForHardwareWallet = securitySettingsViewModel::enableSaveAccessCodeForHardwareWallet,
            onSystemPinRequiredChange = securitySettingsViewModel::onSystemPinRequiredChange
        )
    }
}

private fun HSNavigation.showAppRestartAlert(context: Context, torViewModel: SecurityTorSettingsViewModel) {
    val warningTitle = if (torViewModel.torCheckEnabled) {
        context.getString(R.string.Tor_Connection_Enable)
    } else {
        context.getString(R.string.Tor_Connection_Disable)
    }

    val actionButton = if (torViewModel.torCheckEnabled) {
        context.getString(R.string.Button_Enable)
    } else {
        context.getString(R.string.Button_Disable)
    }

    slideFromBottom(
        ConfirmationDialogSheet(
            icon = R.drawable.ic_tor_connection_24,
            title = context.getString(R.string.Tor_Alert_Title),
            warningTitle = warningTitle,
            warningText = context.getString(R.string.SettingsSecurity_AppRestartWarning),
            actionButtonTitle = actionButton,
            transparentButtonTitle = context.getString(R.string.Alert_Cancel),
            listener = object : ConfirmationDialogSheet.Listener {
                override fun onActionButtonClick() {
                    torViewModel.setTorEnabled()
                }

                override fun onTransparentButtonClick() {
                    torViewModel.resetSwitch()
                }

                override fun onCancelButtonClick() {
                    torViewModel.resetSwitch()
                }
            }
        )
    )
}

@Composable
private fun SecurityCenterScreen(
    securitySettingsViewModel: SecuritySettingsViewModel,
    uiState: SecuritySettingsUiState,
    torViewModel: SecurityTorSettingsViewModel,
    navigation: HSNavigation,
    onTransactionAutoHideEnabledChange: (Boolean) -> Unit,
    onSetTransactionAutoHidePinClicked: () -> Unit,
    onDisableTransactionAutoHidePinClicked: () -> Unit,
    onChangeDisplayClicked: () -> Unit,
    onTransferPasscodeEnabledChange: (Boolean) -> Unit,
    showAppRestartAlert: () -> Unit,
    restartApp: () -> Unit,
    onEnableSaveAccessCodeForHardwareWallet: (Boolean) -> Unit,
    onSystemPinRequiredChange: (Boolean) -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val showBalanceHideOnFlipUnsupported: () -> Unit = {
        HudHelper.showWarningMessage(
            contentView = view,
            resId = R.string.balance_hide_on_flip_unsupported,
            duration = SnackbarDuration.LONG
        )
    }
    PageResumeEffect(onResume = securitySettingsViewModel::update, onPause = {})

    if (torViewModel.restartApp) {
        restartApp()
        torViewModel.appRestarted()
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Settings_SecurityCenter),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                },
            )
        }
    ) {
        Column(
            Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(windowInsets)
        ) {
            PasscodeBlock(
                securitySettingsViewModel,
                navigation
            )

            VSpacer(height = 32.dp)

            CellUniversalLawrenceSection {
                SecurityCenterCell(
                    start = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_off_24),
                            tint = ComposeAppTheme.colors.grey,
                            modifier = Modifier.size(24.dp),
                            contentDescription = null
                        )
                    },
                    center = {
                        body_leah(
                            text = stringResource(id = R.string.Appearance_BalanceAutoHide),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    end = {
                        HsSwitch(
                            checked = uiState.balanceAutoHideEnabled,
                            onCheckedChange = {
                                securitySettingsViewModel.onSetBalanceAutoHidden(it)
                            }
                        )
                    }
                )
            }
            InfoText(
                text = stringResource(R.string.Appearance_BalanceAutoHide_Description),
                paddingBottom = 32.dp
            )

            CellUniversalLawrenceSection {
                SecurityCenterCell(
                    start = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_off_24),
                            tint = ComposeAppTheme.colors.grey,
                            modifier = Modifier.size(24.dp),
                            contentDescription = null
                        )
                    },
                    center = {
                        if (uiState.balanceHideOnFlipSupported) {
                            body_leah(
                                text = stringResource(id = R.string.balance_hide_on_flip_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            body_grey(
                                text = stringResource(id = R.string.balance_hide_on_flip_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (uiState.showFlipNewDot && uiState.balanceHideOnFlipSupported) {
                            NewDot(
                                modifier = Modifier
                                    .align(Alignment.Top)
                                    .padding(start = 4.dp, top = 3.dp)
                            )
                        }
                    },
                    end = {
                        Box {
                            HsSwitch(
                                checked = uiState.balanceHideOnFlipEnabled,
                                onCheckedChange = {
                                    securitySettingsViewModel.onSetBalanceHideOnFlip(it)
                                },
                                enabled = uiState.balanceHideOnFlipSupported,
                            )
                            if (!uiState.balanceHideOnFlipSupported) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(bounded = false, radius = 24.dp),
                                            onClick = showBalanceHideOnFlipUnsupported
                                        )
                                )
                            }
                        }
                    },
                    onClick = if (uiState.balanceHideOnFlipSupported) {
                        null
                    } else {
                        showBalanceHideOnFlipUnsupported
                    },
                )
            }
            InfoText(
                text = stringResource(R.string.balance_hide_on_flip_description),
                paddingBottom = 32.dp
            )

            TransactionAutoHideBlock(
                transactionAutoHideEnabled = uiState.transactionAutoHideEnabled,
                displayLevel = uiState.displayLevel,
                transactionAutoHideSeparatePinExists = uiState.transactionAutoHideSeparatePinExists,
                onTransactionAutoHideEnabledChange = onTransactionAutoHideEnabledChange,
                onPinClicked = onSetTransactionAutoHidePinClicked,
                onDisablePinClicked = onDisableTransactionAutoHidePinClicked,
                onChangeDisplayClicked = onChangeDisplayClicked
            )

            TransferPasscodeBlock(
                transferPasscodeEnabled = uiState.transferPasscodeEnabled,
                onTransferPasscodeEnabledChange = onTransferPasscodeEnabledChange
            )

            TorBlock(
                torViewModel,
                showAppRestartAlert,
            )

            HardwareWalletBiometricBlock(
                enabled = securitySettingsViewModel.uiState.isSaveAccessCodeForHardwareWalletEnabled,
                onValueChanged = onEnableSaveAccessCodeForHardwareWallet
            )

            DuressPasscodeBlock(
                securitySettingsViewModel,
                navigation
            )
            InfoText(
                text = stringResource(R.string.SettingsSecurity_DuressPinDescription),
                paddingBottom = 32.dp
            )

            SystemPinBlock(
                isPinRequired = uiState.isSystemPinRequired,
                enabled = uiState.isSystemPinRequiredEnabled,
                onPinRequiredChange = { checked ->
                    if (!uiState.isSystemPinRequired) {
                        if (!uiState.isDeviceSecure) {
                            HudHelper.showWarningMessage(
                                contentView = view,
                                resId = R.string.need_setup_system_pin,
                                duration = SnackbarDuration.LONG
                            )
                        } else
                            navigation.slideFromBottomForResult<ChecklistConfirmationSheet.Result>(
                                ChecklistConfirmationSheet(
                                    ChecklistConfirmationSheet.ChecklistConfirmationInput(
                                        title = context.getString(R.string.SettingsSecurity_system_pin_enable),
                                        confirmButtonText = context.getString(R.string.confirm),
                                        items = context.resources.getStringArray(R.array.enable_system_pin_confirm)
                                            .toList()
                                    )
                                )
                            ) { result ->
                                if (result.confirmed) {
                                    onSystemPinRequiredChange(true)
                                }
                            }
                    }
                }
            )
        }
    }
}

@Composable
fun SecurityCenterCell(
    start: @Composable RowScope.() -> Unit,
    center: @Composable RowScope.() -> Unit,
    end: @Composable() (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        start.invoke(this)
        Spacer(Modifier.width(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            center(this)
        }
        end?.let {
            Spacer(
                Modifier
                    .defaultMinSize(minWidth = 8.dp)
            )
            end.invoke(this)
        }
    }
}

@Preview
@Composable
private fun SecurityFlipRowPreview() {
    ComposeAppTheme {
        CellUniversalLawrenceSection {
            SecurityCenterCell(
                start = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_off_24),
                        tint = ComposeAppTheme.colors.grey,
                        modifier = Modifier.size(24.dp),
                        contentDescription = null
                    )
                },
                center = {
                    body_leah(
                        text = stringResource(id = R.string.balance_hide_on_flip_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    NewDot(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(start = 4.dp, top = 3.dp)
                    )
                },
                end = {
                    HsSwitch(checked = true, onCheckedChange = {})
                }
            )
        }
    }
}
