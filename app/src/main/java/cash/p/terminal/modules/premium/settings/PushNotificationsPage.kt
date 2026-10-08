package cash.p.terminal.modules.premium.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cash.p.terminal.R
import cash.p.terminal.core.notifications.TransactionNotificationManager
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogBottomSheet
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class PushNotificationsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val pushNotificationsViewModel: PushNotificationsViewModel = koinViewModel()
        val txNotificationManager: TransactionNotificationManager = koinInject()
        val activity = LocalActivity.current

        var showRationaleDialog by rememberSaveable { mutableStateOf(false) }
        var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
        var showRevokedDialog by rememberSaveable { mutableStateOf(false) }
        var showCalculatorModeDialog by rememberSaveable { mutableStateOf(false) }
        var wasPermissionRequested by rememberSaveable { mutableStateOf(false) }

        var hasPermission by remember { mutableStateOf(txNotificationManager.checkAllPermissions()) }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            hasPermission = txNotificationManager.checkAllPermissions()
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            wasPermissionRequested = true
            pushNotificationsViewModel.setShowNotifications(granted)
            if (!granted) {
                val shouldShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity?.shouldShowRequestPermissionRationale(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) ?: false
                } else {
                    false
                }
                if (!shouldShowRationale) {
                    showSettingsDialog = true
                }
            }
        }

        fun enableShowNotifications() {
            if (hasPermission) {
                pushNotificationsViewModel.setShowNotifications(true)
                return
            }
            val hasRuntimePermission = txNotificationManager.hasNotificationPermission()
            if (!hasRuntimePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) ?: false
                when {
                    shouldShowRationale -> showRationaleDialog = true
                    wasPermissionRequested -> showSettingsDialog = true
                    else -> permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                showRevokedDialog = true
            }
        }

        PushNotificationsScreen(
            uiState = pushNotificationsViewModel.uiState,
            noNotificationPermission = !hasPermission,
            onShowNotificationsToggle = { enabled ->
                if (!enabled) {
                    pushNotificationsViewModel.setShowNotifications(false)
                    return@PushNotificationsScreen
                }
                if (pushNotificationsViewModel.uiState.isCalculatorModeEnabled) {
                    showCalculatorModeDialog = true
                } else {
                    enableShowNotifications()
                }
            },
            onPermissionWarningClick = { showRevokedDialog = true },
            onBlockchainNotificationsToggle = pushNotificationsViewModel::setBlockchainNotifications,
            onPollingIntervalChange = pushNotificationsViewModel::setPollingInterval,
            onShowBlockchainNameToggle = pushNotificationsViewModel::setShowBlockchainName,
            onShowCoinAmountToggle = pushNotificationsViewModel::setShowCoinAmount,
            onShowFiatAmountToggle = pushNotificationsViewModel::setShowFiatAmount,
            onClose = { navigation.navigateUpSafely() }
        )

        if (showCalculatorModeDialog) {
            ConfirmationDialogBottomSheet(
                title = stringResource(R.string.calculator_pin_settings_title),
                icon = R.drawable.icon_24_warning_2,
                warningTitle = null,
                warningText = stringResource(R.string.push_notifications_calculator_mode_warning),
                actionButtonTitle = stringResource(R.string.button_enable),
                transparentButtonTitle = stringResource(R.string.button_do_not_enable),
                onCloseClick = { showCalculatorModeDialog = false },
                onActionButtonClick = {
                    showCalculatorModeDialog = false
                    enableShowNotifications()
                },
                onTransparentButtonClick = { showCalculatorModeDialog = false }
            )
        }

        if (showRationaleDialog) {
            ConfirmationDialogBottomSheet(
                title = stringResource(R.string.notification_permission_rationale_title),
                icon = R.drawable.icon_24_warning_2,
                warningTitle = null,
                warningText = stringResource(R.string.notification_permission_rationale_message),
                actionButtonTitle = stringResource(R.string.Button_Ok),
                transparentButtonTitle = stringResource(R.string.Button_Cancel),
                onCloseClick = { showRationaleDialog = false },
                onActionButtonClick = {
                    showRationaleDialog = false
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                },
                onTransparentButtonClick = { showRationaleDialog = false }
            )
        }

        if (showSettingsDialog) {
            ConfirmationDialogBottomSheet(
                title = stringResource(R.string.notification_permission_denied_title),
                icon = R.drawable.icon_24_warning_2,
                warningTitle = null,
                warningText = stringResource(R.string.notification_permission_denied_message),
                actionButtonTitle = stringResource(R.string.button_open_settings),
                transparentButtonTitle = stringResource(R.string.Button_Cancel),
                onCloseClick = { showSettingsDialog = false },
                onActionButtonClick = {
                    showSettingsDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", activity?.packageName ?: "", null)
                    }
                    activity?.startActivity(intent)
                },
                onTransparentButtonClick = { showSettingsDialog = false }
            )
        }

        if (showRevokedDialog) {
            ConfirmationDialogBottomSheet(
                title = stringResource(R.string.notification_permission_revoked_title),
                icon = R.drawable.icon_24_warning_2,
                warningTitle = null,
                warningText = stringResource(R.string.notification_permission_revoked_message),
                actionButtonTitle = stringResource(R.string.button_open_settings),
                transparentButtonTitle = stringResource(R.string.Button_Cancel),
                onCloseClick = { showRevokedDialog = false },
                onActionButtonClick = {
                    showRevokedDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", activity?.packageName ?: "", null)
                    }
                    activity?.startActivity(intent)
                },
                onTransparentButtonClick = { showRevokedDialog = false }
            )
        }
    }
}
