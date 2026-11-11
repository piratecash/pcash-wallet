package cash.p.terminal.modules.pin

import androidx.lifecycle.ViewModel
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager

class SetDuressPinSelectAccountsViewModel(accountManager: IAccountManager) : ViewModel() {

    val watchAccounts: List<Account>
    val regularAccounts: List<Account>

    init {
        val (watch, regular) = accountManager.accounts.partition { it.isWatchAccount }
        watchAccounts = watch
        regularAccounts = regular
    }
}
