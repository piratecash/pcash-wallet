package cash.p.terminal.core.storage

import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.entities.EnabledWallet

class EnabledWalletsStorage(private val router: StorageRouter) : IEnabledWalletStorage {

    override val enabledWallets: List<EnabledWallet>
        get() = router.acrossAccounts { walletsDao().enabledCoins() }

    override fun enabledWallets(accountId: String): List<EnabledWallet> {
        return router.account(accountId)?.walletsDao()?.enabledCoins(accountId).orEmpty()
    }

    override fun save(enabledWallets: List<EnabledWallet>): List<Long> =
        enabledWallets.groupBy { router.account(it.accountId) }
            .flatMap { (store, wallets) -> store?.walletsDao()?.insertWallets(wallets).orEmpty() }

    override fun deleteAll() {
        router.forEachAccount { walletsDao().deleteAll() }
    }

    override fun delete(accountId: String, enabledWalletIds: List<Long>) {
        router.account(accountId)?.walletsDao()?.deleteWallets(enabledWalletIds)
    }

    override fun deleteByTokenQueryId(accountId: String, tokenQueryId: String) {
        router.account(accountId)?.walletsDao()?.deleteByTokenQueryId(accountId, tokenQueryId)
    }
}
