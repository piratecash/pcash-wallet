package cash.p.terminal.core.storage

import cash.p.terminal.entities.NftItemRecord
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NftItemStorage(private val router: StorageRouter) {

    private fun dao(accountId: String) = router.account(accountId)?.nftItemDao()

    fun observe(accountId: String, ownerAddresses: List<String>): Flow<List<NftItemRecord>> =
        dao(accountId)?.observe(accountId, ownerAddresses) ?: flowOf(emptyList())

    suspend fun replace(
        accountId: String,
        blockchainType: BlockchainType,
        ownerAddress: String,
        items: List<NftItemRecord>,
    ) {
        dao(accountId)?.replace(accountId, blockchainType, ownerAddress, items)
    }

    suspend fun deleteByAccountIds(accountIds: List<String>) {
        router.forEachAccount { nftItemDao().deleteByAccountIds(accountIds) }
    }
}
