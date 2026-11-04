package cash.p.terminal.modules.settings.security

import androidx.compose.runtime.Composable
import cash.p.terminal.R
import cash.p.terminal.core.rememberAuthorizedAction
import cash.p.terminal.core.rememberEnsurePinSet
import cash.p.terminal.modules.pin.EditDuressPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetDuressPinIntroPage
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
import cash.p.terminal.modules.settings.security.ui.ManagePasscodeSection
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.PinSet_ForDuress

@Composable
fun DuressPasscodeBlock(
    viewModel: SecuritySettingsViewModel,
    navigation: HSNavigation
) {
    val uiState = viewModel.uiState
    val openManageDuressPin = navigation.rememberAuthorizedAction<Boolean>(input = null) { duressPinEnabled ->
        if (duressPinEnabled) {
            navigation.slideFromRight(EditDuressPinPage())
        } else {
            navigation.slideFromRight(SetDuressPinIntroPage())
        }
    }
    val openSetDuressPin = navigation.rememberEnsurePinSet(Res.string.PinSet_ForDuress, PinType.REGULAR) {
        navigation.slideFromRight(SetDuressPinIntroPage())
    }
    val disableDuressPin = navigation.rememberAuthorizedAction(input = null, action = viewModel::disableDuressPin)

    ManagePasscodeSection(
        iconRes = R.drawable.ic_switch_wallet_24,
        enabled = uiState.duressPinEnabled,
        editTextRes = R.string.SettingsSecurity_EditDuressPin,
        enableTextRes = R.string.SettingsSecurity_SetDuressPin,
        disableTextRes = R.string.SettingsSecurity_DisableDuressPin,
        onManageClick = {
            if (uiState.pinEnabled) {
                openManageDuressPin(uiState.duressPinEnabled)
            } else {
                openSetDuressPin()
            }
        },
        onDisableClick = disableDuressPin
    )
}
