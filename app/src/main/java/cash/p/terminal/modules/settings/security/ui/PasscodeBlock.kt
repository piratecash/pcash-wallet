package cash.p.terminal.modules.settings.security.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.rememberAuthorizedAction
import cash.p.terminal.core.rememberEnsurePinSet
import cash.p.terminal.modules.pin.EditPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.modules.settings.security.SecurityCenterCell
import cash.p.terminal.modules.settings.security.autolock.AutoLockIntervalsPage
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
import cash.p.terminal.modules.settings.main.HsSettingCell
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsSecurity_Biometric_Authentication
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.resources.PinSet_ForBiometrics

@Composable
fun PasscodeBlock(
    viewModel: SecuritySettingsViewModel,
    navigation: HSNavigation,
) {
    val uiState = viewModel.uiState
    val openEditPin = navigation.rememberAuthorizedAction(input = null) {
        navigation.slideFromRight(EditPinPage(null))
    }
    val disablePin = navigation.rememberAuthorizedAction(input = null, action = viewModel::disablePin)
    val enableBiometrics = navigation.rememberEnsurePinSet(
        Res.string.PinSet_ForBiometrics,
        PinType.REGULAR,
        viewModel::enableBiometrics
    )

    VSpacer(height = 8.dp)
    ManagePasscodeSection(
        iconRes = R.drawable.ic_passcode,
        enabled = uiState.pinEnabled,
        editTextRes = R.string.SettingsSecurity_EditPin,
        showWarningWhenDisabled = true,
        onManageClick = {
            if (!uiState.pinEnabled) {
                navigation.slideFromRight(SetPinPage(null))
            } else {
                openEditPin()
            }
        },
        onDisableClick = disablePin
    )

    if (uiState.pinEnabled) {
        VSpacer(32.dp)
        CellUniversalLawrenceSection(
            listOf {
                HsSettingCell(
                    R.string.Settings_AutoLock,
                    R.drawable.ic_lock_20,
                    value = stringResource(uiState.autoLockIntervalName),
                    onClick = {
                        navigation.slideFromRight(AutoLockIntervalsPage())
                    }
                )
            }
        )
    }

    if (viewModel.biometricSettingsVisible) {
        Spacer(Modifier.height(32.dp))
        CellUniversalLawrenceSection {
            SecurityCenterCell(
                start = {
                    Icon(
                        painter = painterResource(R.drawable.icon_touch_id_24),
                        tint = ComposeAppTheme.colors.iconSecondary,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                center = {
                    body_leah(
                        text = stringResource(Res.string.SettingsSecurity_Biometric_Authentication),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                end = {
                    HsSwitch(
                        checked = uiState.biometricsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                enableBiometrics()
                            } else {
                                viewModel.disableBiometrics()
                            }
                        },
                    )
                }
            )
        }
    }
}
