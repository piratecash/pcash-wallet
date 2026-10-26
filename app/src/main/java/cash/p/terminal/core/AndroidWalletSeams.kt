package cash.p.terminal.core

import androidx.compose.runtime.Composable
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.modules.main.MainModule
import cash.p.terminal.navigation.HSNavigation
import io.horizontalsystems.core.BackgroundManager

class PinSecretActionAuthorizer : SecretActionAuthorizer {
    @Composable
    override fun <A> rememberAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit =
        navigation.rememberAuthorizedAction(input = null, action = action)

    @Composable
    override fun rememberHiddenTransactionsAuthorization(navigation: HSNavigation, action: () -> Unit): () -> Unit =
        navigation.rememberAuthorizedShowAllTransactionsAction(action)
}

class ActivityAppRestarter(private val backgroundManager: BackgroundManager) : AppRestarter {
    override fun restart() {
        backgroundManager.currentActivity?.let { MainModule.startAsNewTask(it) }
    }
}
