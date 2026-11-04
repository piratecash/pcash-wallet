package cash.p.terminal.modules.settings.advancedsecurity

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cash.p.terminal.R
import cash.p.terminal.core.fullRestart
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.rememberAuthorizedAction
import cash.p.terminal.core.rememberAuthorizedDeleteContactsPasscodeAction
import cash.p.terminal.core.rememberEnsurePinSet
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.core.slideToDeleteContactsTerms
import cash.p.terminal.modules.calculator.rememberEnableCalculatorMode
import cash.p.terminal.modules.calculator.autolock.CalculatorAutoLockScreen
import cash.p.terminal.modules.calculator.autolock.CalculatorAutoLockViewModel
import cash.p.terminal.modules.calculator.domain.CalculatorModeService
import cash.p.terminal.modules.calculator.pinsettings.CalculatorPinSettingsScreen
import cash.p.terminal.modules.calculator.pinsettings.CalculatorPinSettingsViewModel
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.hiddenwallet.SetHiddenWalletPinPage
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsScreen
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SetSecureResetPinPage
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsScreen
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.slideFromRightSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.PinSet_Title

class AdvancedSecurityPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: AdvancedSecurityViewModel = koinViewModel()
        val openHiddenWalletTerms = navigation.rememberPremiumAction {
            navigation.slideFromRightSafely(HiddenWalletTermsPage())
        }
        val openSecureResetTerms = navigation.rememberPremiumAction {
            navigation.slideFromRightSafely(SecureResetTermsPage())
        }
        val disableSecureReset =
            navigation.rememberAuthorizedAction(input = null, action = viewModel::onSecureResetDisabled)
        val openDeleteContactsTerms = navigation.rememberPremiumAction(navigation::slideToDeleteContactsTerms)
        val disableDeleteContacts =
            navigation.rememberAuthorizedDeleteContactsPasscodeAction(viewModel::onDeleteContactsDisabled)

        AdvancedSecurityScreen(
            uiState = viewModel.uiState,
            onCreateHiddenWalletClick = openHiddenWalletTerms,
            onSecureResetToggle = { enabled ->
                if (enabled) openSecureResetTerms() else disableSecureReset()
            },
            onDeleteContactsToggle = { enabled ->
                if (enabled) openDeleteContactsTerms() else disableDeleteContacts()
            },
            onCalculatorPinClick = {
                navigation.slideFromRight(CalculatorPinPage())
            },
            onClose = navigation::navigateUpSafely
        )
    }
}

class HiddenWalletTermsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val context = LocalContext.current
        val termTitles =
            context.resources.getStringArray(R.array.AdvancedSecurity_Terms_Checkboxes)

        val viewModel: HiddenWalletTermsViewModel = koinViewModel {
            parametersOf(termTitles)
        }
        val uiState = viewModel.uiState
        val openSetHiddenWalletPin = navigation.rememberEnsurePinSet(Res.string.PinSet_Title, PinType.REGULAR) {
            navigation.slideFromRight(SetHiddenWalletPinPage())
        }

        HiddenWalletTermsScreen(
            uiState = uiState,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = openSetHiddenWalletPin,
            onNavigateBack = navigation::navigateUpSafely
        )
    }
}

class SecureResetTermsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: AdvancedSecurityViewModel = koinViewModel(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(AdvancedSecurityPage::class)
        )
        val context = LocalContext.current
        val termTitles = context.resources.getStringArray(R.array.SecureReset_Terms_Checkboxes)

        val termsViewModel: SecureResetTermsViewModel = koinViewModel {
            parametersOf(termTitles)
        }
        val uiState = termsViewModel.uiState
        val openSetSecureResetPin = navigation.rememberEnsurePinSet(Res.string.PinSet_Title, PinType.REGULAR) {
            navigation.slideFromRight(SetSecureResetPinPage())
            viewModel.onSecureResetEnabled()
        }

        SecureResetTermsScreen(
            uiState = uiState,
            onCheckboxToggle = termsViewModel::toggleCheckbox,
            onAgreeClick = openSetSecureResetPin,
            onNavigateBack = navigation::navigateUpSafely
        )
    }
}

class CalculatorPinPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val pinViewModel: CalculatorPinSettingsViewModel = koinViewModel()
        val activity = LocalActivity.current
        val enableCalculatorMode = navigation.rememberEnableCalculatorMode()
        val disableCalculatorMode = navigation.rememberAuthorizedAction(input = null) {
            getKoinInstance<CalculatorModeService>().disable()
            activity?.fullRestart()
        }
        CalculatorPinSettingsScreen(
            uiState = pinViewModel.uiState,
            onToggleCalculator = { enabled, disablePushNotifications ->
                if (enabled) enableCalculatorMode(disablePushNotifications) else disableCalculatorMode()
            },
            onAutoLockClick = {
                navigation.slideFromRight(CalculatorAutoLockPage())
            },
            onClose = navigation::navigateUp,
        )
    }
}

class CalculatorAutoLockPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val autoLockViewModel: CalculatorAutoLockViewModel = koinViewModel()
        CalculatorAutoLockScreen(
            uiState = autoLockViewModel.uiState,
            onSelect = { option ->
                autoLockViewModel.onSelect(option)
                navigation.navigateUp()
            },
            onClose = navigation::navigateUp,
        )
    }
}
