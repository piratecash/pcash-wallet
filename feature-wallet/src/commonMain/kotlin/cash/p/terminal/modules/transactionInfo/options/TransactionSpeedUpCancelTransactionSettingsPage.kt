package cash.p.terminal.modules.transactionInfo.options

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import org.koin.compose.viewmodel.koinViewModel

class TransactionSpeedUpCancelTransactionSettingsPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<TransactionSpeedUpCancelViewModel>(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(TransactionSpeedUpCancelPage::class)
        )

        val sendTransactionService = viewModel.sendTransactionService

        sendTransactionService.GetSettingsContent(navigation)
    }
}
