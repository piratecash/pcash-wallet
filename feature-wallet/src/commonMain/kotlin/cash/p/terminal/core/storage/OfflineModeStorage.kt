package cash.p.terminal.core.storage

import cash.p.terminal.entities.OfflineBlockchain
import io.horizontalsystems.core.entities.BlockchainType

class OfflineModeStorage(private val router: StorageRouter) {

    private fun dao(accountId: String) = router.account(accountId)?.offlineBlockchainDao()

    fun getAll(): List<OfflineBlockchain> = router.acrossAccounts { offlineBlockchainDao().getAll() }

    suspend fun setMode(
        accountId: String,
        blockchainType: BlockchainType,
        offline: Boolean,
        enabledAt: Long?,
    ) {
        dao(accountId)?.setMode(accountId, blockchainType, offline, enabledAt)
    }

    suspend fun setLastSynced(
        accountId: String,
        blockchainType: BlockchainType,
        lastSyncedAt: Long,
    ) {
        dao(accountId)?.setLastSynced(accountId, blockchainType, lastSyncedAt)
    }

    suspend fun deleteByAccount(accountId: String) {
        dao(accountId)?.deleteByAccount(accountId)
    }

    suspend fun deleteChain(accountId: String, blockchainType: BlockchainType) {
        dao(accountId)?.deleteChain(accountId, blockchainType)
    }
}
