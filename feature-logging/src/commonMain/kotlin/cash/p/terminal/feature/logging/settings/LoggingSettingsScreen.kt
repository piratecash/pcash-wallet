package cash.p.terminal.feature.logging.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.logging.components.DeleteLogsConfirmationDialog
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.button_info
import cash.p.terminal.resources.button_open_settings
import cash.p.terminal.resources.camera_permission_denied_message
import cash.p.terminal.resources.camera_permission_denied_title
import cash.p.terminal.resources.delete_all_contacts
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.icon_24_warning_2
import cash.p.terminal.resources.login_logging_auto_delete
import cash.p.terminal.resources.login_logging_delete_all
import cash.p.terminal.resources.login_logging_delete_auth_data
import cash.p.terminal.resources.login_logging_delete_auth_data_description
import cash.p.terminal.resources.login_logging_delete_period
import cash.p.terminal.resources.login_logging_duress_mode
import cash.p.terminal.resources.login_logging_info_description
import cash.p.terminal.resources.login_logging_log_into_duress
import cash.p.terminal.resources.login_logging_log_successful
import cash.p.terminal.resources.login_logging_log_unsuccessful
import cash.p.terminal.resources.login_logging_passcode
import cash.p.terminal.resources.login_logging_selfie_on_duress_login
import cash.p.terminal.resources.login_logging_selfie_on_successful_login
import cash.p.terminal.resources.login_logging_selfie_on_unsuccessful_login
import cash.p.terminal.resources.login_logging_selfie_unsuccessful_shared
import cash.p.terminal.resources.login_logging_send_notification
import cash.p.terminal.resources.login_logging_standard_login
import cash.p.terminal.resources.login_logging_subscription_expired_description
import cash.p.terminal.resources.login_logging_subscription_expired_title
import cash.p.terminal.resources.login_logging_title
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSectionAnimated
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.RowWithArrow
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.components.SwitchWithTextWarning
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VFillSpacer
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_disabled
import cash.p.terminal.ui_compose.components.body_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import io.horizontalsystems.core.entities.AutoDeletePeriod
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoggingSettingsScreen(
    uiState: LoggingSettingsUiState,
    cameraAccess: LoginCameraAccess,
    onLogSuccessfulLoginsToggle: (Boolean) -> Unit,
    onSelfieOnSuccessfulLoginToggle: (Boolean) -> Unit,
    onLogUnsuccessfulLoginsToggle: (Boolean) -> Unit,
    onSelfieOnUnsuccessfulLoginToggle: (Boolean) -> Unit,
    onLogIntoDuressModeToggle: (Boolean) -> Unit,
    onSelfieOnDuressLoginToggle: (Boolean) -> Unit,
    onPasscodeToggle: (Boolean) -> Unit,
    onDeleteAllContactsPasscodeToggle: (Boolean) -> Unit,
    onSendLoginNotificationClick: (() -> Unit)?,
    onDeleteAllAuthDataOnDuressToggle: (Boolean) -> Unit,
    onAutoDeletePeriodChanged: (AutoDeletePeriod) -> Unit,
    onDeleteAllLogs: () -> Unit,
    onClose: () -> Unit,
    unsuccessfulSelfieIsShared: Boolean = false,
) {
    var showPeriodSelector by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showCameraPermissionDeniedDialog by remember { mutableStateOf(false) }

    var selfieAwaitingPermission by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val cameraPermission = cameraAccess.rememberPermission { granted ->
        if (granted) {
            selfieAwaitingPermission?.invoke(true)
        } else {
            showCameraPermissionDeniedDialog = true
        }
        selfieAwaitingPermission = null
    }

    fun onSelfieToggle(enabled: Boolean, setEnabled: (Boolean) -> Unit) {
        when {
            !enabled -> setEnabled(false)
            !uiState.isPremiumActive -> Unit
            cameraPermission.isGranted -> setEnabled(true)
            cameraPermission.shouldShowRationale -> showCameraPermissionDeniedDialog = true
            else -> {
                selfieAwaitingPermission = setEnabled
                cameraPermission.request()
            }
        }
    }

    val noCameraPermissions = !cameraPermission.isGranted

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.login_logging_title),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.button_info),
                        icon = Res.drawable.ic_info_20,
                        tint = ComposeAppTheme.colors.iconSecondary,
                        onClick = { showInfoSheet = true }
                    )
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Subscription expired warning banner
            VSpacer(12.dp)
            @Suppress("ComplexCondition")
            if (!uiState.isPremiumActive &&
                (uiState.logSuccessfulLoginsEnabled ||
                uiState.logUnsuccessfulLoginsEnabled ||
                uiState.logIntoDuressModeEnabled)
            ) {
                TextImportantError(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(Res.string.login_logging_subscription_expired_title),
                    text = stringResource(Res.string.login_logging_subscription_expired_description),
                    icon = Res.drawable.icon_24_warning_2
                )
                VSpacer(16.dp)
            }

            // STANDARD LOGIN section
            HeaderText(stringResource(Res.string.login_logging_standard_login))
            CellUniversalLawrenceSectionAnimated(
                primaryContent = {
                    SwitchWithText(
                        text = stringResource(Res.string.login_logging_log_successful),
                        checked = uiState.logSuccessfulLoginsEnabled,
                        onCheckedChange = { enabled ->
                            onLogSuccessfulLoginsToggle(enabled)
                        }
                    )
                },
                animatedContent = {
                    SwitchWithTextWarning(
                        text = stringResource(Res.string.login_logging_selfie_on_successful_login),
                        borderTop = true,
                        checked = uiState.selfieOnSuccessfulLoginEnabled,
                        showWarning = noCameraPermissions && uiState.selfieOnSuccessfulLoginEnabled,
                        onWarningIconClick = { showCameraPermissionDeniedDialog = true },
                        onCheckedChange = { enabled -> onSelfieToggle(enabled, onSelfieOnSuccessfulLoginToggle) }
                    )
                },
                animatedVisible = uiState.logSuccessfulLoginsEnabled
            )
            VSpacer(16.dp)
            CellUniversalLawrenceSectionAnimated(
                primaryContent = {
                    SwitchWithText(
                        text = stringResource(Res.string.login_logging_log_unsuccessful),
                        checked = uiState.logUnsuccessfulLoginsEnabled,
                        onCheckedChange = { enabled ->
                            onLogUnsuccessfulLoginsToggle(enabled)
                        }
                    )
                },
                animatedContent = {
                    SwitchWithTextWarning(
                        text = stringResource(Res.string.login_logging_selfie_on_unsuccessful_login),
                        borderTop = true,
                        checked = uiState.selfieOnUnsuccessfulLoginEnabled,
                        showWarning = noCameraPermissions && uiState.selfieOnUnsuccessfulLoginEnabled,
                        onWarningIconClick = { showCameraPermissionDeniedDialog = true },
                        onCheckedChange = { enabled -> onSelfieToggle(enabled, onSelfieOnUnsuccessfulLoginToggle) }
                    )
                },
                animatedVisible = uiState.logUnsuccessfulLoginsEnabled
            )
            if (unsuccessfulSelfieIsShared && uiState.logUnsuccessfulLoginsEnabled) {
                InfoText(text = stringResource(Res.string.login_logging_selfie_unsuccessful_shared))
            }
            // DURESS MODE section
            VSpacer(16.dp)
            HeaderText(stringResource(Res.string.login_logging_duress_mode))
            CellUniversalLawrenceSectionAnimated(
                primaryContent = {
                    SwitchWithText(
                        text = stringResource(Res.string.login_logging_log_into_duress),
                        checked = uiState.logIntoDuressModeEnabled,
                        onCheckedChange = { enabled ->
                            onLogIntoDuressModeToggle(enabled)
                        }
                    )
                },
                animatedContent = {
                    SwitchWithTextWarning(
                        text = stringResource(Res.string.login_logging_selfie_on_duress_login),
                        borderTop = true,
                        checked = uiState.selfieOnDuressLoginEnabled,
                        showWarning = noCameraPermissions && uiState.selfieOnDuressLoginEnabled,
                        onWarningIconClick = { showCameraPermissionDeniedDialog = true },
                        onCheckedChange = { enabled -> onSelfieToggle(enabled, onSelfieOnDuressLoginToggle) }
                    )
                },
                afterAnimatedContent = {
                    Column {
                        SwitchWithText(
                            text = stringResource(Res.string.delete_all_contacts),
                            borderTop = true,
                            checked = uiState.deleteContactsPasscodeEnabled,
                            onCheckedChange = onDeleteAllContactsPasscodeToggle
                        )
                        onSendLoginNotificationClick?.let { onClick ->
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = ComposeAppTheme.colors.borderDivider,
                            )
                            RowWithArrow(
                                text = stringResource(Res.string.login_logging_send_notification),
                                showAlert = !uiState.isPremiumActive,
                                onClick = onClick
                            )
                        }
                    }
                },
                animatedVisible = uiState.logIntoDuressModeEnabled
            )

            VSpacer(16.dp)
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(Res.string.login_logging_delete_auth_data),
                    checked = uiState.deleteAllAuthDataOnDuressEnabled,
                    onCheckedChange = onDeleteAllAuthDataOnDuressToggle
                )
            }
            InfoText(
                text = stringResource(Res.string.login_logging_delete_auth_data_description),
            )

            // Passcode
            VSpacer(28.dp)
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(Res.string.login_logging_passcode),
                    checked = uiState.passcodeEnabled,
                    onCheckedChange = onPasscodeToggle
                )
            }

            VFillSpacer(32.dp)

            // Auto Deleting Data Logs
            CellUniversalLawrenceSection(
                listOf {
                    HsSettingCell(
                        title = Res.string.login_logging_auto_delete,
                        value = uiState.autoDeletePeriod.shortTitle.getString(),
                        onClick = { showPeriodSelector = true }
                    )
                }
            )

            VSpacer(16.dp)

            // Delete All Logs button
            CellUniversalLawrenceSection(
                listOf {
                    RowUniversal(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        onClick = if (uiState.deleteButtonEnabled) {
                            { showDeleteConfirmation = true }
                        } else null
                    ) {
                        val tintColor = if (uiState.deleteButtonEnabled) {
                            ComposeAppTheme.colors.statusError
                        } else {
                            ComposeAppTheme.colors.iconDisabled
                        }
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete_20),
                            contentDescription = null,
                            tint = tintColor
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        if (uiState.deleteButtonEnabled) {
                            body_lucian(text = stringResource(Res.string.login_logging_delete_all))
                        } else {
                            body_disabled(text = stringResource(Res.string.login_logging_delete_all))
                        }
                    }
                }
            )
            VSpacer(32.dp)
        }
    }

    // Period Selector Dialog
    if (showPeriodSelector) {
        AlertGroup(
            title = Res.string.login_logging_delete_period,
            select = Select(uiState.autoDeletePeriod, AutoDeletePeriod.entries),
            onSelect = { selected ->
                onAutoDeletePeriodChanged(selected)
                showPeriodSelector = false
            },
            onDismiss = { showPeriodSelector = false }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        DeleteLogsConfirmationDialog(
            onConfirm = {
                onDeleteAllLogs()
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }

    // Info Bottom Sheet
    if (showInfoSheet) {
        InfoBottomSheet(
            title = stringResource(Res.string.login_logging_title),
            text = stringResource(Res.string.login_logging_info_description),
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showCameraPermissionDeniedDialog) {
        CameraPermissionDeniedSheet(
            canOpenSettings = cameraPermission.canOpenSettings,
            onOpenSettings = {
                cameraPermission.openSettings()
                showCameraPermissionDeniedDialog = false
            },
            onDismiss = { showCameraPermissionDeniedDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraPermissionDeniedSheet(
    canOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    TransparentModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ConfirmationDialog(
            title = stringResource(Res.string.camera_permission_denied_title),
            iconPainter = painterResource(Res.drawable.icon_24_warning_2),
            warningTitle = null,
            warningText = stringResource(Res.string.camera_permission_denied_message),
            actionButtonTitle = stringResource(Res.string.button_open_settings).takeIf { canOpenSettings },
            transparentButtonTitle = stringResource(Res.string.Button_Cancel),
            onCloseClick = { hideThen(onDismiss) },
            onActionButtonClick = { hideThen(onOpenSettings) },
            onTransparentButtonClick = { hideThen(onDismiss) }
        )
    }
}

@Preview
@Composable
private fun LoggingSettingsScreenPreview() {
    ComposeAppTheme {
        LoggingSettingsScreen(
            cameraAccess = PreviewLoginCameraAccess,
            uiState = LoggingSettingsUiState(
                passcodeEnabled = false,
                deleteContactsPasscodeEnabled = false,
                logSuccessfulLoginsEnabled = true,
                selfieOnSuccessfulLoginEnabled = false,
                logUnsuccessfulLoginsEnabled = true,
                logIntoDuressModeEnabled = false,
                deleteAllAuthDataOnDuressEnabled = false,
                autoDeletePeriod = AutoDeletePeriod.YEAR,
                deleteButtonEnabled = true,
                noCameraPermissions = false,
                isPremiumActive = false
            ),
            onSelfieOnSuccessfulLoginToggle = {},
            onLogSuccessfulLoginsToggle = {},
            onLogUnsuccessfulLoginsToggle = {},
            onSelfieOnUnsuccessfulLoginToggle = {},
            onPasscodeToggle = {},
            onDeleteAllContactsPasscodeToggle = {},
            onLogIntoDuressModeToggle = {},
            onSelfieOnDuressLoginToggle = {},
            onSendLoginNotificationClick = {},
            onDeleteAllAuthDataOnDuressToggle = {},
            onAutoDeletePeriodChanged = {},
            onDeleteAllLogs = {},
            onClose = {},
            unsuccessfulSelfieIsShared = true,
        )
    }
}

private object PreviewLoginCameraAccess : LoginCameraAccess {
    @Composable
    override fun rememberPermission(onResult: (granted: Boolean) -> Unit) = object : LoginCameraPermission {
        override val isGranted = false
        override val shouldShowRationale = false
        override fun request() = Unit
        override fun openSettings() = Unit
    }
}
