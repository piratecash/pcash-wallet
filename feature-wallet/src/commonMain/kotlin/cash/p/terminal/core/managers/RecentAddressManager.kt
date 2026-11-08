package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.RecentAddress
import cash.p.terminal.wallet.ActionCompletedDelegate
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.entities.BlockchainType

class RecentAddressManager(
    private val accountManager: IAccountManager,
    private val router: StorageRouter,
) {
    private val actionCompletedDelegate = ActionCompletedDelegate
    fun setRecentAddress(address: Address, blockchainType: BlockchainType) {
        accountManager.activeAccount?.let { activeAccount ->
            router.account(activeAccount.id)?.recentAddressDao()
                ?.insert(RecentAddress(activeAccount.id, blockchainType, address.hex))
            actionCompletedDelegate.notifyContactAdded()
        }
    }

    fun getRecentAddress(blockchainType: BlockchainType): String? {
        return accountManager.activeAccount?.let { activeAccount ->
            router.account(activeAccount.id)?.recentAddressDao()?.get(activeAccount.id, blockchainType)?.address
        }
    }

}
