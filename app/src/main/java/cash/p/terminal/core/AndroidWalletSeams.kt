package cash.p.terminal.core

import androidx.compose.runtime.Composable
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.modules.main.MainModule
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Unlock_EnterPasscode
import io.horizontalsystems.core.BackgroundManager

class PinSecretActionAuthorizer : SecretActionAuthorizer {
    @Composable
    override fun <A> rememberAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit =
        navigation.rememberAuthorizedAction(input = null, action = action)

    @Composable
    override fun rememberHiddenTransactionsAuthorization(navigation: HSNavigation, action: () -> Unit): () -> Unit =
        navigation.rememberAuthorizedShowAllTransactionsAction(action)

    @Composable
    override fun <A> rememberTransferAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit =
        navigation.rememberAuthorizedAction(
            ConfirmPinPage.InputConfirm(description = Res.string.Unlock_EnterPasscode, pinType = PinType.TRANSFER),
            action
        )
}

class ActivityAppRestarter(private val backgroundManager: BackgroundManager) : AppRestarter {
    override fun restart() {
        backgroundManager.currentActivity?.let { MainModule.startAsNewTask(it) }
    }
}
