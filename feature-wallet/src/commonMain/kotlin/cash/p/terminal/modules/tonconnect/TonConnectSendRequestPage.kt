package cash.p.terminal.modules.tonconnect

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.tonkit.models.SignTransaction
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class TonConnectSendRequestPage(private val request: SignTransaction) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: TonConnectSendRequestViewModel = koinViewModel { parametersOf(request) }
        TonConnectSendRequestScreen(navigation, viewModel)
    }
}
