package cash.p.terminal.modules.premium.settings

import androidx.compose.runtime.Composable
import cash.p.terminal.R
import cash.p.terminal.feature.logging.settings.LoggingSettingsScreen
import cash.p.terminal.feature.logging.settings.LoggingSettingsViewModel
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.premium.smsnotification.SendSmsNotificationPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.slideFromRightSafely
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.authorizedDeleteContactsPasscodeAction
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.core.ensurePinSetPremiumAction
import cash.p.terminal.core.premiumAction
import cash.p.terminal.core.slideToDeleteContactsTerms
import org.koin.compose.viewmodel.koinViewModel

class LoginLoggingPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val loggingSettingsViewModel: LoggingSettingsViewModel = koinViewModel()

        LoggingSettingsScreen(
            uiState = loggingSettingsViewModel.uiState,
            onLogSuccessfulLoginsToggle = navigation.ensurePinSetPremiumAction(
                descriptionResId = R.string.pin_set_for_login_logging,
                setter = loggingSettingsViewModel::setLogSuccessfulLoginsEnabled
            ),
            onSelfieOnSuccessfulLoginToggle = navigation.ensurePinSetPremiumAction(
                descriptionResId = R.string.pin_set_for_login_logging,
                setter = loggingSettingsViewModel::setSelfieOnSuccessfulLoginEnabled
            ),
            onLogUnsuccessfulLoginsToggle = navigation.ensurePinSetPremiumAction(
                descriptionResId = R.string.pin_set_for_login_logging,
                setter = loggingSettingsViewModel::setLogUnsuccessfulLoginsEnabled
            ),
            onSelfieOnUnsuccessfulLoginToggle = navigation.ensurePinSetPremiumAction(
                descriptionResId = R.string.pin_set_for_login_logging,
                setter = loggingSettingsViewModel::setSelfieOnUnsuccessfulLoginEnabled
            ),
            onLogIntoDuressModeToggle = navigation.ensurePinSetPremiumAction(
                descriptionResId = R.string.pin_set_for_login_logging,
                setter = loggingSettingsViewModel::setLogIntoDuressModeEnabled
            ),
            onSelfieOnDuressLoginToggle = navigation.ensurePinSetPremiumAction(
                descriptionResId = R.string.pin_set_for_login_logging,
                setter = loggingSettingsViewModel::onSelfieOnDuressLoginEnabled
            ),
            onPasscodeToggle = passcodeToggleAction(navigation, loggingSettingsViewModel),
            onDeleteAllContactsPasscodeToggle = { enabled ->
                if (enabled) {
                    navigation.premiumAction {
                        navigation.slideToDeleteContactsTerms()
                    }
                } else {
                    navigation.authorizedDeleteContactsPasscodeAction {
                        loggingSettingsViewModel.disableDeleteContactsPin()
                    }
                }
            },
            onSendLoginNotificationClick = {
                navigation.premiumAction {
                    navigation.ensurePinSet(R.string.pin_set_for_login_logging) {
                        navigation.slideFromRightSafely(SendSmsNotificationPage())
                    }
                }
            },
            onDeleteAllAuthDataOnDuressToggle = navigation.ensurePinSetPremiumAction(
                descriptionResId = R.string.pin_set_for_login_logging,
                setter = loggingSettingsViewModel::setDeleteAllAuthDataOnDuressEnabled
            ),
            onAutoDeletePeriodChanged = loggingSettingsViewModel::setAutoDeletePeriod,
            onDeleteAllLogs = loggingSettingsViewModel::deleteAllLogs,
            onClose = { navigation.navigateUpSafely() }
        )
    }

    private fun passcodeToggleAction(
        navigation: HSNavigation,
        loggingSettingsViewModel: LoggingSettingsViewModel,
    ): (Boolean) -> Unit = navigation.ensurePinSetPremiumAction(
        descriptionResId = R.string.pin_set_for_login_logging_passcode,
        pinType = PinType.LOG_LOGGING
    ) { enabled ->
        if (!enabled) {
            navigation.authorizedAction(
                ConfirmPinPage.InputConfirm(
                    descriptionResId = R.string.confirm_pin_to_disable_login_logging_passcode,
                    pinType = PinType.LOG_LOGGING
                )
            ) {
                loggingSettingsViewModel.disablePasscodeLoggingPin()
            }
        }
        loggingSettingsViewModel.updatePasscodeLoggingState()
    }
}
