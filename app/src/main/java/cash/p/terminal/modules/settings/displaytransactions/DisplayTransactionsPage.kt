package cash.p.terminal.modules.settings.displaytransactions

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import org.koin.compose.viewmodel.koinViewModel

class DisplayTransactionsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: DisplayTransactionsViewModel = koinViewModel()
        DisplayTransactionsScreen(
            selectedItem = viewModel.uiState.collectAsStateWithLifecycle(TransactionDisplayLevel.NOTHING).value,
            onItemSelected = {
                viewModel.onItemSelected(it)
                navigation.navigateUpSafely()
            },
            onBackPressed = {
                navigation.navigateUpSafely()
            }
        )
    }
}
