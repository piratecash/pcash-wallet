package cash.p.terminal.core.storage

import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.entities.EnabledWallet

class EnabledWalletsStorage(private val databaseProvider: AppDatabaseProvider) : IEnabledWalletStorage {

    private val dao: EnabledWalletsDao
        get() = databaseProvider.database.walletsDao()

    override val enabledWallets: List<EnabledWallet>
        get() = dao.enabledCoins()

    override fun enabledWallets(accountId: String): List<EnabledWallet> {
        return dao.enabledCoins(accountId)
    }

    override fun save(enabledWallets: List<EnabledWallet>): List<Long> =
        dao.insertWallets(enabledWallets)

    override fun deleteAll() {
        dao.deleteAll()
    }

    override fun delete(enabledWalletIds: List<Long>) {
        dao.deleteWallets(enabledWalletIds)
    }

    override fun deleteByTokenQueryId(accountId: String, tokenQueryId: String) {
        dao.deleteByTokenQueryId(accountId, tokenQueryId)
    }
}
