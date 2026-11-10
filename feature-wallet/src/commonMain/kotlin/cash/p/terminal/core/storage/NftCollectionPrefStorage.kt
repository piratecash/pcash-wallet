package cash.p.terminal.core.storage

import cash.p.terminal.entities.NftCollectionPrefRecord
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow

class NftCollectionPrefStorage(private val databaseProvider: AppDatabaseProvider) {

    private val dao get() = databaseProvider.database.nftCollectionPrefDao()

    fun observe(accountId: String): Flow<List<NftCollectionPrefRecord>> = dao.observe(accountId)

    suspend fun get(accountId: String, blockchainType: BlockchainType, collectionKey: String) =
        dao.get(accountId, blockchainType, collectionKey)

    suspend fun upsert(record: NftCollectionPrefRecord) = dao.upsert(record)

    suspend fun deleteByAccountIds(accountIds: List<String>) = dao.deleteByAccountIds(accountIds)
}
