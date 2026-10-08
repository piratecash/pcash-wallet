package cash.p.terminal.modules.settings.security

import androidx.compose.runtime.Composable
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.modules.pin.EditDuressPinPage
import cash.p.terminal.modules.pin.SetDuressPinIntroPage
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
import cash.p.terminal.modules.settings.security.ui.ManagePasscodeSection
import cash.p.terminal.navigation.HSNavigation

@Composable
fun DuressPasscodeBlock(
    viewModel: SecuritySettingsViewModel,
    navigation: HSNavigation
) {
    val uiState = viewModel.uiState

    ManagePasscodeSection(
        iconRes = R.drawable.ic_switch_wallet_24,
        enabled = uiState.duressPinEnabled,
        editTextRes = R.string.SettingsSecurity_EditDuressPin,
        enableTextRes = R.string.SettingsSecurity_SetDuressPin,
        disableTextRes = R.string.SettingsSecurity_DisableDuressPin,
        onManageClick = {
            if (uiState.pinEnabled) {
                navigation.authorizedAction {
                    if (uiState.duressPinEnabled) {
                        navigation.slideFromRight(EditDuressPinPage())
                    } else {
                        navigation.slideFromRight(SetDuressPinIntroPage())
                    }
                }
            } else {
                navigation.ensurePinSet(R.string.PinSet_ForDuress) {
                    navigation.slideFromRight(SetDuressPinIntroPage())
                }
            }
        },
        onDisableClick = {
            navigation.authorizedAction {
                viewModel.disableDuressPin()
            }
        }
    )
}
