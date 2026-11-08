package cash.p.terminal.core.storage

import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HardwarePublicKeyStorage(private val router: StorageRouter) : IHardwarePublicKeyStorage {

    private fun dao(accountId: String) = router.account(accountId)?.hardwarePublicKeyDao()

    override fun deleteAll() {
        router.forEachAccount { hardwarePublicKeyDao().deleteAll() }
    }

    override suspend fun save(keys: List<HardwarePublicKey>) {
        router.writePerStore(keys, { it.accountId }) { hardwarePublicKeyDao().insertKeys(it) }
    }

    override suspend fun getKey(accountId: String, blockchainType: BlockchainType, tokenType: TokenType) =
        withContext(Dispatchers.IO) {
            dao(accountId)?.getPublicKey(
                accountId = accountId,
                blockchainType = blockchainType.uid,
                tokenType = tokenType
            )
        }

    override suspend fun getKeyByBlockchain(accountId: String, blockchainType: BlockchainType) =
        withContext(Dispatchers.IO) {
            dao(accountId)?.getPublicKeyByBlockchain(
                accountId = accountId,
                blockchainType = blockchainType.uid
            )
        }

    override suspend fun getAllPublicKeys(accountId: String): List<HardwarePublicKey> =
        withContext(Dispatchers.IO) {
            dao(accountId)?.getAllPublicKeys(
                accountId = accountId
            ).orEmpty()
        }
}
