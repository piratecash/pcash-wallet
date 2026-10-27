package cash.p.terminal.modules.premium.settings

import androidx.compose.runtime.Composable
import cash.p.terminal.feature.logging.settings.LoggingSettingsScreen
import cash.p.terminal.feature.logging.settings.LoggingSettingsViewModel
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.premium.smsnotification.SendSmsNotificationPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.slideFromRightSafely
import cash.p.terminal.core.rememberAuthorizedAction
import cash.p.terminal.core.rememberAuthorizedDeleteContactsPasscodeAction
import cash.p.terminal.core.rememberEnsurePinSet
import cash.p.terminal.core.rememberEnsurePinSetPremiumAction
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.core.slideToDeleteContactsTerms
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.confirm_pin_to_disable_login_logging_passcode
import cash.p.terminal.resources.pin_set_for_login_logging
import cash.p.terminal.resources.pin_set_for_login_logging_passcode

class LoginLoggingPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val loggingSettingsViewModel: LoggingSettingsViewModel = koinViewModel()
        val openDeleteContactsTerms = navigation.rememberPremiumAction(navigation::slideToDeleteContactsTerms)
        val disableDeleteContactsPin = navigation.rememberAuthorizedDeleteContactsPasscodeAction(
            loggingSettingsViewModel::disableDeleteContactsPin
        )
        val openSendLoginNotification = navigation.rememberPremiumAction(
            navigation.rememberEnsurePinSet(Res.string.pin_set_for_login_logging, PinType.REGULAR) {
                navigation.slideFromRightSafely(SendSmsNotificationPage())
            }
        )

        LoggingSettingsScreen(
            uiState = loggingSettingsViewModel.uiState,
            onLogSuccessfulLoginsToggle = navigation.rememberLoginLoggingToggle(
                loggingSettingsViewModel::setLogSuccessfulLoginsEnabled
            ),
            onSelfieOnSuccessfulLoginToggle = navigation.rememberLoginLoggingToggle(
                loggingSettingsViewModel::setSelfieOnSuccessfulLoginEnabled
            ),
            onLogUnsuccessfulLoginsToggle = navigation.rememberLoginLoggingToggle(
                loggingSettingsViewModel::setLogUnsuccessfulLoginsEnabled
            ),
            onSelfieOnUnsuccessfulLoginToggle = navigation.rememberLoginLoggingToggle(
                loggingSettingsViewModel::setSelfieOnUnsuccessfulLoginEnabled
            ),
            onLogIntoDuressModeToggle = navigation.rememberLoginLoggingToggle(
                loggingSettingsViewModel::setLogIntoDuressModeEnabled
            ),
            onSelfieOnDuressLoginToggle = navigation.rememberLoginLoggingToggle(
                loggingSettingsViewModel::onSelfieOnDuressLoginEnabled
            ),
            onPasscodeToggle = rememberPasscodeToggleAction(navigation, loggingSettingsViewModel),
            onDeleteAllContactsPasscodeToggle = { enabled ->
                if (enabled) openDeleteContactsTerms() else disableDeleteContactsPin()
            },
            onSendLoginNotificationClick = openSendLoginNotification,
            onDeleteAllAuthDataOnDuressToggle = navigation.rememberLoginLoggingToggle(
                loggingSettingsViewModel::setDeleteAllAuthDataOnDuressEnabled
            ),
            onAutoDeletePeriodChanged = loggingSettingsViewModel::setAutoDeletePeriod,
            onDeleteAllLogs = loggingSettingsViewModel::deleteAllLogs,
            onClose = { navigation.navigateUpSafely() }
        )
    }

    @Composable
    private fun HSNavigation.rememberLoginLoggingToggle(setter: (Boolean) -> Unit): (Boolean) -> Unit =
        rememberEnsurePinSetPremiumAction(Res.string.pin_set_for_login_logging, PinType.REGULAR, setter)

    @Composable
    private fun rememberPasscodeToggleAction(
        navigation: HSNavigation,
        loggingSettingsViewModel: LoggingSettingsViewModel,
    ): (Boolean) -> Unit {
        val disablePasscode = navigation.rememberAuthorizedAction(
            ConfirmPinPage.InputConfirm(
                description = Res.string.confirm_pin_to_disable_login_logging_passcode,
                pinType = PinType.LOG_LOGGING
            ),
            loggingSettingsViewModel::disablePasscodeLoggingPin
        )
        return navigation.rememberEnsurePinSetPremiumAction(
            description = Res.string.pin_set_for_login_logging_passcode,
            pinType = PinType.LOG_LOGGING
        ) { enabled ->
            if (!enabled) disablePasscode()
            loggingSettingsViewModel.updatePasscodeLoggingState()
        }
    }
}
