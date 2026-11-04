package cash.p.terminal.modules.settings.appearance

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.core.fullRestart
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.rememberAuthorizedAction
import cash.p.terminal.modules.calculator.CalculatorModePushNotificationsWarningDialog
import cash.p.terminal.modules.calculator.domain.CalculatorModeService
import cash.p.terminal.modules.calculator.rememberEnableCalculatorMode
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely

class AppIconPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<AppIconViewModel>()
        val activity = LocalActivity.current
        var showCalculatorPushWarning by rememberSaveable { mutableStateOf(false) }
        val enableCalculatorMode = navigation.rememberEnableCalculatorMode()
        val switchFromCalculatorMode = navigation.rememberAuthorizedAction<AppIcon>(input = null) { icon ->
            getKoinInstance<CalculatorModeService>().disableAndSwitchTo(icon)
            activity?.fullRestart()
        }

        CalculatorModePushNotificationsWarningDialog(
            onCloseClick = { showCalculatorPushWarning = false },
            onDisablePushClick = {
                showCalculatorPushWarning = false
                enableCalculatorMode(true)
            },
            onKeepPushClick = {
                showCalculatorPushWarning = false
                enableCalculatorMode(false)
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
                        enableCalculatorMode(false)
                    }

                    viewModel.uiState.isCalculatorModeEnabled -> {
                        switchFromCalculatorMode(icon)
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
