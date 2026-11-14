package cash.p.terminal.core.storage

import cash.p.terminal.entities.NftCollectionPrefRecord
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NftCollectionPrefStorage(private val router: StorageRouter) {

    private fun dao(accountId: String) = router.account(accountId)?.nftCollectionPrefDao()

    fun observe(accountId: String): Flow<List<NftCollectionPrefRecord>> =
        dao(accountId)?.observe(accountId) ?: flowOf(emptyList())

    suspend fun get(accountId: String, blockchainType: BlockchainType, collectionKey: String) =
        dao(accountId)?.get(accountId, blockchainType, collectionKey)

    suspend fun upsert(record: NftCollectionPrefRecord) {
        dao(record.accountId)?.upsert(record)
    }

    suspend fun deleteByAccountIds(accountIds: List<String>) {
        router.forEachAccount { nftCollectionPrefDao().deleteByAccountIds(accountIds) }
    }
}
