package cash.p.terminal.modules.calculator

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import cash.p.terminal.core.fullRestart
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.rememberEnsurePinSet
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.modules.calculator.domain.CalculatorModeService
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.navigation.HSNavigation
import io.horizontalsystems.core.IPinComponent
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.PinSet_Title

private data class CalculatorModeRequest(val pinExistedBefore: Boolean, val disablePushNotifications: Boolean)

/** Premium and a PIN are required; the app restarts in calculator mode. The argument is disablePushNotifications. */
@Composable
internal fun HSNavigation.rememberEnableCalculatorMode(): (Boolean) -> Unit {
    val activity = LocalActivity.current
    val enable = rememberPremiumAction(
        rememberEnsurePinSet<CalculatorModeRequest>(Res.string.PinSet_Title, PinType.REGULAR) { request ->
            getKoinInstance<CalculatorModeService>().enable(
                pinExistedBefore = request.pinExistedBefore,
                disablePushNotifications = request.disablePushNotifications,
            )
            activity?.fullRestart()
        }
    )
    return { disablePushNotifications ->
        enable(CalculatorModeRequest(getKoinInstance<IPinComponent>().isPinSet, disablePushNotifications))
    }
}
