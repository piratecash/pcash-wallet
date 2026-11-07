package cash.p.terminal.ledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class LedgerSetupPage(private val accountName: String) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<LedgerWalletViewModel> { parametersOf(accountName) }

        LaunchedEffect(viewModel.uiState.success) {
            if (viewModel.uiState.success) {
                navigation.setResult(this@LedgerSetupPage, Result(success = true))
                navigation.navigateUp()
            }
        }

        LedgerSetupScreen(
            uiState = viewModel.uiState,
            onConnect = viewModel::connect,
            onDismissSkippedApps = viewModel::dismissSkippedApps,
            onBack = navigation::navigateUpSafely,
        )
    }

    data class Result(val success: Boolean)
}
