package cash.p.terminal.core.storage

import cash.p.terminal.entities.PendingTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class PendingTransactionStorage(private val router: StorageRouter) {
    private fun dao(walletId: String) = router.account(walletId)?.pendingTransactionDao()

    suspend fun insert(entity: PendingTransactionEntity) {
        dao(entity.walletId)?.insert(entity)
    }

    suspend fun updateTxId(id: String, txId: String) {
        router.forEachAccount { pendingTransactionDao().updateTxId(id, txId) }
    }

    suspend fun getById(id: String): PendingTransactionEntity? =
        router.firstInAccounts { pendingTransactionDao().getById(id) }

    fun getActivePendingFlow(walletId: String): Flow<List<PendingTransactionEntity>> =
        dao(walletId)?.getActivePendingFlow(System.currentTimeMillis(), walletId) ?: flowOf(emptyList())

    suspend fun getPendingForWallet(walletId: String): List<PendingTransactionEntity> =
        dao(walletId)?.getPendingForWallet(walletId, System.currentTimeMillis()).orEmpty()

    suspend fun markBalanceConfirmed(ids: List<String>, confirmedAt: Long) {
        if (ids.isEmpty()) return
        router.forEachAccount { pendingTransactionDao().markBalanceConfirmed(ids, confirmedAt) }
    }

    suspend fun rebaseSdkBalanceAtCreation(ids: List<String>, atomic: String) {
        if (ids.isEmpty()) return
        router.forEachAccount { pendingTransactionDao().rebaseSdkBalanceAtCreation(ids, atomic) }
    }

    suspend fun deleteById(id: String) {
        router.forEachAccount { pendingTransactionDao().deleteById(id) }
    }

    suspend fun getExpired(): List<PendingTransactionEntity> =
        router.acrossAccounts { pendingTransactionDao().getExpired(System.currentTimeMillis()) }

    suspend fun hasPendingTransactions(): Boolean {
        return router.accounts.any { it.pendingTransactionDao().getAllPending().isNotEmpty() }
    }

    suspend fun deleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        router.forEachAccount { pendingTransactionDao().deleteByIds(ids) }
    }
}
