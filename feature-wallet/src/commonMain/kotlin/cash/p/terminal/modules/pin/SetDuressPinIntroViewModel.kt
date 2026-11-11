package cash.p.terminal.modules.pin

import androidx.lifecycle.ViewModel
import cash.p.terminal.wallet.IAccountManager

class SetDuressPinIntroViewModel(accountManager: IAccountManager) : ViewModel() {
    val shouldShowSelectAccounts = accountManager.accounts.isNotEmpty()
}
