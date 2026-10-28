package cash.p.terminal.modules.send.evm.settings

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.send.evm.confirmation.SendEvmConfirmationPage
import cash.p.terminal.modules.send.evm.confirmation.SendEvmConfirmationViewModel
import cash.p.terminal.modules.send.rememberExistingViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

class SendEvmSettingsPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SendEvmSettingsScreen(navigation)
    }
}

@Composable
fun SendEvmSettingsScreen(navigation: HSNavigation) {
    val viewModel = navigation.rememberExistingViewModel(
        SendEvmConfirmationPage::class,
        SendEvmConfirmationViewModel::class
    ) ?: return

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navigation)
}
