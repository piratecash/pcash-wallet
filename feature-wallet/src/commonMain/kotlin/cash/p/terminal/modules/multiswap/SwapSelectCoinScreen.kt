package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.inject

@Composable
fun SwapSelectCoinScreen(
    navigation: HSNavigation,
    token: Token?,
    title: String?,
    onSelect: (Token) -> Unit
) {
    val accountManager: IAccountManager by inject(IAccountManager::class.java)
    val activeAccount = accountManager.activeAccount ?: return

    val viewModel = koinViewModel<SwapSelectCoinViewModel> {
        parametersOf(token, activeAccount)
    }
    val uiState = viewModel.uiState

    SelectSwapCoinDialogScreen(
        title = title ?: "",
        coinBalanceItems = uiState.coinBalanceItems,
        loading = uiState.loading,
        onSearchTextChanged = viewModel::setQuery,
        onClose = navigation::navigateUpSafely,
        onClickItem = { onSelect(it.token) },
        fiatItems = uiState.fiatItems,
        hasFiatSection = uiState.hasFiatSection,
    )
}
