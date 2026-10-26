package cash.p.terminal.core

import cash.p.terminal.R
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.modules.main.MainModule
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.navigation.HSNavigation
import io.horizontalsystems.core.BackgroundManager

class PinSecretActionAuthorizer : SecretActionAuthorizer {
    override fun authorize(navigation: HSNavigation, action: () -> Unit) =
        navigation.authorizedAction(action = action)

    override fun authorizeHiddenTransactions(navigation: HSNavigation, action: () -> Unit) =
        navigation.authorizedAction(
            ConfirmPinPage.InputConfirm(
                descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                pinType = PinType.TRANSACTIONS_HIDE
            ),
            action
        )
}

class ActivityAppRestarter(private val backgroundManager: BackgroundManager) : AppRestarter {
    override fun restart() {
        backgroundManager.currentActivity?.let { MainModule.startAsNewTask(it) }
    }
}
