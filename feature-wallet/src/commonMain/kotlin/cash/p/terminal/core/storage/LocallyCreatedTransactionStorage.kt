package cash.p.terminal.core.storage

import cash.p.terminal.entities.LocallyCreatedTransactionRecord

class LocallyCreatedTransactionStorage(
    private val router: StorageRouter,
) {
    private fun dao(accountId: String) = router.account(accountId)?.locallyCreatedTransactionDao()

    suspend fun exists(
        accountId: String,
        blockchainTypeUid: String,
        transactionHash: String,
    ): Boolean = dao(accountId)?.exists(accountId, blockchainTypeUid, transactionHash) == true

    suspend fun insert(record: LocallyCreatedTransactionRecord): Boolean =
        dao(record.accountId)?.insert(record).let { it != null && it != -1L }

    suspend fun trimAccount(accountId: String, limit: Int) {
        dao(accountId)?.trimAccount(accountId, limit)
    }

    suspend fun trimAllAccounts(limit: Int) {
        router.forEachAccount {
            val dao = locallyCreatedTransactionDao()
            dao.getAccountIds().forEach { accountId -> dao.trimAccount(accountId, limit) }
        }
    }

    suspend fun deleteByAccountIds(accountIds: List<String>) {
        if (accountIds.isEmpty()) return
        router.forEachAccount { locallyCreatedTransactionDao().deleteByAccountIds(accountIds) }
    }

    suspend fun count(accountId: String): Int = dao(accountId)?.count(accountId) ?: 0
}
