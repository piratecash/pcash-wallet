package cash.p.terminal.modules.settings.advancedsecurity

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.authorizedDeleteContactsPasscodeAction
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.core.fullRestart
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.premiumAction
import cash.p.terminal.core.slideToDeleteContactsTerms
import cash.p.terminal.modules.calculator.autolock.CalculatorAutoLockScreen
import cash.p.terminal.modules.calculator.autolock.CalculatorAutoLockViewModel
import cash.p.terminal.modules.calculator.domain.CalculatorModeService
import cash.p.terminal.modules.calculator.pinsettings.CalculatorPinSettingsScreen
import cash.p.terminal.modules.calculator.pinsettings.CalculatorPinSettingsViewModel
import cash.p.terminal.modules.pin.hiddenwallet.SetHiddenWalletPinPage
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsScreen
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SetSecureResetPinPage
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsScreen
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import io.horizontalsystems.core.IPinComponent
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.slideFromRightSafely

class AdvancedSecurityPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: AdvancedSecurityViewModel = koinViewModel()

        AdvancedSecurityScreen(
            uiState = viewModel.uiState,
            onCreateHiddenWalletClick = {
                navigation.premiumAction {
                    navigation.slideFromRightSafely(HiddenWalletTermsPage())
                }
            },
            onSecureResetToggle = { enabled ->
                if (enabled) {
                    navigation.premiumAction {
                        navigation.slideFromRightSafely(SecureResetTermsPage())
                    }
                } else {
                    navigation.authorizedAction {
                        viewModel.onSecureResetDisabled()
                    }
                }
            },
            onDeleteContactsToggle = { enabled ->
                if (enabled) {
                    navigation.premiumAction {
                        navigation.slideToDeleteContactsTerms()
                    }
                } else {
                    navigation.authorizedDeleteContactsPasscodeAction {
                        viewModel.onDeleteContactsDisabled()
                    }
                }
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

        HiddenWalletTermsScreen(
            uiState = uiState,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = {
                navigation.ensurePinSet(R.string.PinSet_Title) {
                    navigation.slideFromRight(SetHiddenWalletPinPage())
                }
            },
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

        SecureResetTermsScreen(
            uiState = uiState,
            onCheckboxToggle = termsViewModel::toggleCheckbox,
            onAgreeClick = {
                navigation.ensurePinSet(R.string.PinSet_Title) {
                    navigation.slideFromRight(SetSecureResetPinPage())
                    viewModel.onSecureResetEnabled()
                }
            },
            onNavigateBack = navigation::navigateUpSafely
        )
    }
}

class CalculatorPinPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val pinViewModel: CalculatorPinSettingsViewModel = koinViewModel()
        val activity = LocalActivity.current
        CalculatorPinSettingsScreen(
            uiState = pinViewModel.uiState,
            onToggleCalculator = { enabled, disablePushNotifications ->
                val calculatorModeService = getKoinInstance<CalculatorModeService>()
                if (enabled) {
                    val pinExistedBefore = getKoinInstance<IPinComponent>().isPinSet
                    navigation.premiumAction {
                        navigation.ensurePinSet(R.string.PinSet_Title) {
                            calculatorModeService.enable(
                                pinExistedBefore = pinExistedBefore,
                                disablePushNotifications = disablePushNotifications,
                            )
                            activity?.fullRestart()
                        }
                    }
                } else {
                    navigation.authorizedAction {
                        calculatorModeService.disable()
                        activity?.fullRestart()
                    }
                }
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
