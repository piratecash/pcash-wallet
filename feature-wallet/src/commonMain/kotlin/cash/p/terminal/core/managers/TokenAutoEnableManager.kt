package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.storage.TokenAutoEnabledBlockchainDao
import cash.p.terminal.entities.TokenAutoEnabledBlockchain
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType

class TokenAutoEnableManager(
    private val databaseProvider: AppDatabaseProvider
) {
    private val tokenAutoEnabledBlockchainDao: TokenAutoEnabledBlockchainDao
        get() = databaseProvider.database.tokenAutoEnabledBlockchainDao()

    fun markAutoEnable(account: Account, blockchainType: BlockchainType) {
        tokenAutoEnabledBlockchainDao.insert(TokenAutoEnabledBlockchain(account.id, blockchainType))
    }

    fun isAutoEnabled(account: Account, blockchainType: BlockchainType): Boolean {
        return tokenAutoEnabledBlockchainDao.get(account.id, blockchainType) != null
    }
}
