package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.modules.balance.BalanceAccountsViewModel
import cash.p.terminal.modules.balance.BalanceScreenState
import cash.p.terminal.navigation.HSNavigation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun BalanceScreen(
    navigation: HSNavigation,
    paddingValues: PaddingValues,
    refreshRequests: Flow<Unit> = emptyFlow(),
) {
    val viewModel = koinViewModel<BalanceAccountsViewModel>()

    when (val tmpAccount = viewModel.balanceScreenState) {
        BalanceScreenState.NoAccount -> BalanceNoAccount(navigation, paddingValues)
        is BalanceScreenState.HasAccount -> {
            BalanceForAccount(
                navigation = navigation,
                accountViewItem = tmpAccount.accountViewItem,
                paddingValuesParent = paddingValues,
                refreshRequests = refreshRequests,
            )
        }

        else -> {}
    }
}
