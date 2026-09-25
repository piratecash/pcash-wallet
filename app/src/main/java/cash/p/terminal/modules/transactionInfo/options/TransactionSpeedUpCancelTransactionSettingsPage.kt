package cash.p.terminal.modules.transactionInfo.options

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage

class TransactionSpeedUpCancelTransactionSettingsPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = viewModel<TransactionSpeedUpCancelViewModel>(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(TransactionSpeedUpCancelPage::class)
        )

        val sendTransactionService = viewModel.sendTransactionService

        sendTransactionService.GetSettingsContent(navigation)
    }
}
