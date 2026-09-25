package cash.p.terminal.modules.walletconnect.request

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.modules.walletconnect.request.sendtransaction.WCSendEthereumTransactionRequestViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage

class WCEvmTransactionSettingsPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        WCEvmTransactionSettingsScreen(navigation)
    }
}

@Composable
fun WCEvmTransactionSettingsScreen(navigation: HSNavigation) {
    // Without the request page there is no ViewModel to show, and it cannot be created here.
    if (navigation.backStack.none { it is WCRequestPage }) return

    val viewModel = viewModel<WCSendEthereumTransactionRequestViewModel>(
        viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(WCRequestPage::class)
    )

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navigation)
}
