package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.modules.balance.BalanceAccountsViewModel
import cash.p.terminal.modules.balance.BalanceScreenState
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.navigation.HSNavigation

@Composable
fun BalanceScreen(
    navigation: HSNavigation,
    paddingValues: PaddingValues,
    onOpenTransactionInfo: (TransactionItem) -> Unit,
) {
    val viewModel = koinViewModel<BalanceAccountsViewModel>()

    when (val tmpAccount = viewModel.balanceScreenState) {
        BalanceScreenState.NoAccount -> BalanceNoAccount(navigation, paddingValues)
        is BalanceScreenState.HasAccount -> {
            BalanceForAccount(
                navigation = navigation,
                accountViewItem = tmpAccount.accountViewItem,
                paddingValuesParent = paddingValues,
                onOpenTransactionInfo = onOpenTransactionInfo,
            )
        }

        else -> {}
    }
}
