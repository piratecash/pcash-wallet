package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.entities.TokenAutoEnabledBlockchain
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType

class TokenAutoEnableManager(
    private val router: StorageRouter
) {
    private fun dao(account: Account) = router.account(account.id)?.tokenAutoEnabledBlockchainDao()

    fun markAutoEnable(account: Account, blockchainType: BlockchainType) {
        dao(account)?.insert(TokenAutoEnabledBlockchain(account.id, blockchainType))
    }

    fun isAutoEnabled(account: Account, blockchainType: BlockchainType): Boolean {
        return dao(account)?.get(account.id, blockchainType) != null
    }
}
