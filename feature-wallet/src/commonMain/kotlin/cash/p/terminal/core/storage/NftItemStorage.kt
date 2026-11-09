package cash.p.terminal.core.storage

import cash.p.terminal.entities.NftItemRecord
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow

class NftItemStorage(private val databaseProvider: AppDatabaseProvider) {

    private val dao get() = databaseProvider.database.nftItemDao()

    fun observe(accountId: String, ownerAddresses: List<String>): Flow<List<NftItemRecord>> =
        dao.observe(accountId, ownerAddresses)

    suspend fun replace(
        accountId: String,
        blockchainType: BlockchainType,
        ownerAddress: String,
        items: List<NftItemRecord>,
    ) = dao.replace(accountId, blockchainType, ownerAddress, items)

    suspend fun deleteByAccountIds(accountIds: List<String>) = dao.deleteByAccountIds(accountIds)
}
