package cash.p.terminal.modules.balance

import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.core.storage.acrossAccounts
import cash.p.terminal.core.storage.writePerStore
import cash.p.terminal.entities.EnabledWalletCache
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.getUniqueKey
import java.util.concurrent.atomic.AtomicReference

class BalanceCache(private val router: StorageRouter) {
    // Read on first lookup, not at construction: screens build this on the main thread.
    private val stored: Map<String, BalanceData> by lazy {
        convertToCacheMap(router.acrossAccounts { enabledWalletsCacheDao().getAll() })
    }

    private val updated = AtomicReference<Map<String, BalanceData>>(emptyMap())

    private fun convertToCacheMap(list: List<EnabledWalletCache>): Map<String, BalanceData> {
        return list.map {
            val key = listOf(it.tokenQueryId, it.accountId).joinToString()
            key to BalanceData(
                available = it.balance,
                timeLocked = it.balanceLocked,
                stackingUnpaid = it.stackingUnpaid
            )
        }.toMap()
    }

    fun setCache(wallet: Wallet, balanceData: BalanceData) {
        setCache(mapOf(wallet to balanceData))
    }

    fun getCache(wallet: Wallet): BalanceData? {
        val key = wallet.getUniqueKey()
        return updated.get()[key] ?: stored[key]
    }

    fun setCache(balancesData: Map<Wallet, BalanceData>) {
        val list = balancesData.map { (wallet, balanceData) ->
            EnabledWalletCache(
                tokenQueryId = wallet.token.tokenQuery.id,
                accountId = wallet.account.id,
                balance = balanceData.available + balanceData.pending,
                balanceLocked = balanceData.timeLocked,
                stackingUnpaid = balanceData.stackingUnpaid
            )
        }
        val entries = convertToCacheMap(list)
        updated.updateAndGet { it + entries }

        router.writePerStore(list, { it.accountId }) { enabledWalletsCacheDao().insertAll(it) }
    }

}
