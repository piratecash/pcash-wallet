package cash.p.terminal.modules.eip20approve

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.modules.send.rememberExistingViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely

class Eip20ApproveTransactionSettingsPage(val input: Eip20ApprovePage.Input) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        Eip20ApproveTransactionSettingsScreen(navigation)
    }
}

@Composable
fun Eip20ApproveTransactionSettingsScreen(navigation: HSNavigation) {
    val viewModel = navigation.rememberExistingViewModel(
        Eip20ApprovePage::class,
        Eip20ApproveViewModel::class
    ) ?: return

    val sendTransactionService = viewModel.sendTransactionService
    if (sendTransactionService == null) {
        LaunchedEffect(navigation) {
            navigation.navigateUpSafely()
        }
        return
    }

    sendTransactionService.GetSettingsContent(navigation)
}
