package cash.p.terminal.modules.settings.appearance

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.core.fullRestart
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.premiumAction
import cash.p.terminal.modules.calculator.CalculatorModePushNotificationsWarningDialog
import cash.p.terminal.modules.calculator.domain.CalculatorModeService
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import io.horizontalsystems.core.IPinComponent

class AppIconPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: AppearanceViewModel = viewModel(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(AppearancePage::class),
            factory = AppearanceModule.Factory()
        )
        val activity = LocalActivity.current
        var showCalculatorPushWarning by rememberSaveable { mutableStateOf(false) }

        CalculatorModePushNotificationsWarningDialog(
            onCloseClick = { showCalculatorPushWarning = false },
            onDisablePushClick = {
                showCalculatorPushWarning = false
                enableCalculatorIcon(activity, navigation, true)
            },
            onKeepPushClick = {
                showCalculatorPushWarning = false
                enableCalculatorIcon(activity, navigation, false)
            },
            visible = showCalculatorPushWarning,
        )

        AppIconScreen(
            appIconOptions = viewModel.uiState.appIconOptions,
            onAppIconSelect = { icon ->
                when {
                    icon == AppIcon.Calculator && viewModel.uiState.pushNotificationsEnabled -> {
                        showCalculatorPushWarning = true
                    }

                    icon == AppIcon.Calculator -> {
                        enableCalculatorIcon(activity, navigation, false)
                    }

                    viewModel.uiState.isCalculatorModeEnabled -> {
                        navigation.authorizedAction {
                            getKoinInstance<CalculatorModeService>().disableAndSwitchTo(icon)
                            activity?.fullRestart()
                        }
                    }

                    else -> {
                        viewModel.onEnterAppIcon(icon)
                        activity?.fullRestart()
                    }
                }
            },
            onClose = navigation::navigateUpSafely
        )
    }
}

private fun enableCalculatorIcon(
    activity: Activity?,
    navigation: HSNavigation,
    disablePushNotifications: Boolean,
) {
    val pinExistedBefore = getKoinInstance<IPinComponent>().isPinSet
    navigation.premiumAction {
        navigation.ensurePinSet(R.string.PinSet_Title) {
            getKoinInstance<CalculatorModeService>().enable(
                pinExistedBefore = pinExistedBefore,
                disablePushNotifications = disablePushNotifications,
            )
            activity?.fullRestart()
        }
    }
}
