package cash.p.terminal.modules.receive.viewmodels

import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import androidx.lifecycle.ViewModel
import cash.p.terminal.modules.receive.ui.UsedAddressesParams
import cash.p.terminal.wallet.entities.FullCoin

class ReceiveSharedViewModel(
    private val accountManager: IAccountManager,
    private val marketKit: MarketKitWrapper,
) : ViewModel() {

    var wallet: cash.p.terminal.wallet.Wallet? = null
    var coinUid: String? = null
    var usedAddressesParams: UsedAddressesParams? = null

    val activeAccount: cash.p.terminal.wallet.Account?
        get() = accountManager.activeAccount

    fun fullCoin(): FullCoin? {
        val coinUid = coinUid ?: return null
        return marketKit.fullCoins(listOf(coinUid)).firstOrNull()
    }

}
