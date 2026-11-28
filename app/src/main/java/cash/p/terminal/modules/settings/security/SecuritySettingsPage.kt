package cash.p.terminal.modules.settings.security

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import cash.p.terminal.R
import cash.p.terminal.ui.compose.components.NewDot
import cash.p.terminal.core.rememberAuthorizedAction
import cash.p.terminal.core.rememberEnsurePinSet
import cash.p.terminal.core.fullRestart
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.EditPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.modules.settings.displaytransactions.DisplayTransactionsPage
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsUiState
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
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
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.HudHelper
import io.horizontalsystems.core.ui.dialogs.ChecklistConfirmationSheet
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.PinSet_Title
import cash.p.terminal.resources.PinSet_Transactions_Hide
import cash.p.terminal.resources.Unlock_EnterPasscode
import cash.p.terminal.resources.Unlock_EnterPasscode_Transactions_Hide
import cash.p.terminal.resources.Unlock_EnterPasscode_Transfer

class SecuritySettingsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val torViewModel = koinViewModel<SecurityTorSettingsViewModel>()
        val securitySettingsViewModel = koinViewModel<SecuritySettingsViewModel>()
        val activity = LocalActivity.current
        val transactionsHideInput = ConfirmPinPage.InputConfirm(
            description = Res.string.Unlock_EnterPasscode_Transactions_Hide,
            pinType = PinType.TRANSACTIONS_HIDE
        )
        val enableTransactionAutoHide = navigation.rememberEnsurePinSet(Res.string.PinSet_Title, PinType.REGULAR) {
            securitySettingsViewModel.onTransactionAutoHideEnabledChange(true)
        }
        val disableTransactionAutoHide = navigation.rememberAuthorizedAction(transactionsHideInput) {
            securitySettingsViewModel.onTransactionAutoHideEnabledChange(false)
        }
        val openDisplayTransactions = navigation.rememberAuthorizedAction(transactionsHideInput) {
            navigation.slideFromRight(DisplayTransactionsPage())
        }
        val openSetTransactionAutoHidePin = navigation.rememberAuthorizedAction(
            ConfirmPinPage.InputConfirm(
                description = Res.string.Unlock_EnterPasscode,
                pinType = PinType.REGULAR
            )
        ) {
            navigation.slideFromRight(
                SetPinPage(
                    SetPinPage.Input(
                        description = Res.string.PinSet_Transactions_Hide,
                        pinType = PinType.TRANSACTIONS_HIDE
                    )
                )
            )
        }
        val openEditTransactionAutoHidePin = navigation.rememberAuthorizedAction(transactionsHideInput) {
            navigation.slideFromRight(
                EditPinPage(
                    SetPinPage.Input(
                        Res.string.PinSet_Transactions_Hide,
                        PinType.TRANSACTIONS_HIDE
                    )
                )
            )
        }
        val disableTransactionAutoHidePin = navigation.rememberAuthorizedAction(transactionsHideInput) {
            securitySettingsViewModel.onDisableTransactionAutoHidePin()
        }
        val enableTransferPasscode = navigation.rememberEnsurePinSet(Res.string.PinSet_Title, PinType.REGULAR) {
            securitySettingsViewModel.onTransferPasscodeEnabledChange(true)
        }
        val disableTransferPasscode = navigation.rememberAuthorizedAction(
            ConfirmPinPage.InputConfirm(
                description = Res.string.Unlock_EnterPasscode_Transfer,
                pinType = PinType.REGULAR
            )
        ) {
            securitySettingsViewModel.onTransferPasscodeEnabledChange(false)
        }

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
                        enableTransactionAutoHide()
                    }
                } else {
                    disableTransactionAutoHide()
                }
            },
            onChangeDisplayClicked = openDisplayTransactions,
            onSetTransactionAutoHidePinClicked = {
                if (!securitySettingsViewModel.uiState.transactionAutoHideSeparatePinExists) {
                    openSetTransactionAutoHidePin()
                } else {
                    openEditTransactionAutoHidePin()
                }
            },
            onDisableTransactionAutoHidePinClicked = disableTransactionAutoHidePin,
            restartApp = { activity?.fullRestart() },
            onTransferPasscodeEnabledChange = { enabled ->
                if (enabled) {
                    if (securitySettingsViewModel.uiState.pinEnabled) {
                        securitySettingsViewModel.onTransferPasscodeEnabledChange(true)
                    } else {
                        enableTransferPasscode()
                    }
                } else {
                    disableTransferPasscode()
                }
            },
            onEnableSaveAccessCodeForHardwareWallet = securitySettingsViewModel::enableSaveAccessCodeForHardwareWallet,
            onSystemPinRequiredChange = securitySettingsViewModel::onSystemPinRequiredChange
        )
    }
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
    restartApp: () -> Unit,
    onEnableSaveAccessCodeForHardwareWallet: (Boolean) -> Unit,
    onSystemPinRequiredChange: (Boolean) -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val systemPinConfirmation = navigation.rememberResultLauncher<ChecklistConfirmationSheet.Result> { result ->
        if (result.confirmed) {
            onSystemPinRequiredChange(true)
        }
    }
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
        containerColor = ComposeAppTheme.colors.backgroundBase,
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
                            tint = ComposeAppTheme.colors.iconSecondary,
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
                            tint = ComposeAppTheme.colors.iconSecondary,
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
                onPinClick = onSetTransactionAutoHidePinClicked,
                onDisablePinClick = onDisableTransactionAutoHidePinClicked,
                onChangeDisplayClick = onChangeDisplayClicked
            )

            TransferPasscodeBlock(
                transferPasscodeEnabled = uiState.transferPasscodeEnabled,
                onTransferPasscodeEnabledChange = onTransferPasscodeEnabledChange
            )

            TorBlock(
                torViewModel,
                navigation,
            )

            HardwareWalletBiometricBlock(
                enabled = securitySettingsViewModel.uiState.isSaveAccessCodeForHardwareWalletEnabled,
                onValueChanged = onEnableSaveAccessCodeForHardwareWallet
            )

            if (uiState.duressAvailable) {
                DuressPasscodeBlock(
                    securitySettingsViewModel,
                    navigation
                )
                InfoText(
                    text = stringResource(R.string.SettingsSecurity_DuressPinDescription),
                    paddingBottom = 32.dp
                )
            }

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
                            systemPinConfirmation.slideFromBottom(
                                ChecklistConfirmationSheet(
                                    ChecklistConfirmationSheet.ChecklistConfirmationInput(
                                        title = context.getString(R.string.SettingsSecurity_system_pin_enable),
                                        confirmButtonText = context.getString(R.string.confirm),
                                        items = context.resources.getStringArray(R.array.enable_system_pin_confirm)
                                            .toList()
                                    )
                                )
                            )
                    }
                }
            )
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
                        tint = ComposeAppTheme.colors.iconSecondary,
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
