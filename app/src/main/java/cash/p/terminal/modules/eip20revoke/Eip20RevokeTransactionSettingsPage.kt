package cash.p.terminal.modules.eip20revoke

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.send.rememberExistingViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator

class Eip20RevokeTransactionSettingsPage(val input: Eip20RevokeConfirmPage.Input) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        Eip20RevokeTransactionSettingsScreen(navigation)
    }
}

@Composable
fun Eip20RevokeTransactionSettingsScreen(navigation: HSNavigation) {
    val viewModel = navigation.rememberExistingViewModel(
        Eip20RevokeConfirmPage::class,
        Eip20RevokeConfirmViewModel::class
    ) ?: return

    val uiState = viewModel.uiState
    val sendTransactionService = viewModel.sendTransactionService
    if (sendTransactionService == null) {
        if (!uiState.preparing) {
            LaunchedEffect(navigation) {
                navigation.navigateUpSafely()
            }
        }
        Eip20RevokeTransactionSettingsLoading()
        return
    }

    sendTransactionService.GetSettingsContent(navigation)
}

@Composable
private fun Eip20RevokeTransactionSettingsLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        HSCircularProgressIndicator(progress = 0.15f, size = 32.dp)
    }
}
