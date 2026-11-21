package cash.p.terminal.modules.tonconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.core.managers.DAppRequest
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.wallet.IAccountManager
import org.koin.compose.koinInject

/** Opens the TON Connect sheets for requests arriving from dApps; compose it at the root of a signed-in session. */
@Composable
fun TonConnectRequestEffects(
    navigation: HSNavigation,
    onConnectResult: (request: DAppRequest, approved: Boolean) -> Unit,
) {
    val tonConnectManager = koinInject<TonConnectManager>()
    val accountManager = koinInject<IAccountManager>()

    val pendingSendRequest by tonConnectManager.pendingSendRequest.collectAsStateWithLifecycle()
    // A request of a hidden account waits for the level that shows it.
    val accounts by accountManager.accountsFlow.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(pendingSendRequest, accounts) {
        tonConnectManager.takeShowableSendRequest()?.let {
            navigation.slideFromBottom(TonConnectSendRequestPage(it))
        }
    }

    val launcher = navigation.rememberResultLauncher<TonConnectNewPage.Result, DAppRequest> { result, request ->
        onConnectResult(request, result.approved)
    }
    LaunchedEffect(Unit) {
        tonConnectManager.dappRequestFlow.collect { launcher.slideFromBottom(TonConnectNewPage(it.uri), it) }
    }
}
