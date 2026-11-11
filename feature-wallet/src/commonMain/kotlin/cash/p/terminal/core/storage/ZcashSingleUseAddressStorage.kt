package cash.p.terminal.core.storage

import cash.p.terminal.entities.ZcashSingleUseAddress
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class ZcashSingleUseAddressStorage(
    private val router: StorageRouter,
    private val dispatcherProvider: DispatcherProvider
) {
    private fun dao(accountId: String) = router.account(accountId)?.zcashSingleUseAddressDao()

    suspend fun saveAddress(address: ZcashSingleUseAddress) = withContext(dispatcherProvider.io) {
        dao(address.accountId)?.insert(address)
    }

    suspend fun getNextAddress(accountId: String): ZcashSingleUseAddress? =
        withContext(dispatcherProvider.io) {
            dao(accountId)?.getNextUnusedAddress(accountId)
        }

    suspend fun incrementUseCount(accountId: String, address: String) =
        withContext(dispatcherProvider.io) {
            dao(accountId)?.incrementUseCount(accountId, address)
        }

    suspend fun updateHadBalance(accountId: String, address: String, hadBalance: Boolean) =
        withContext(dispatcherProvider.io) {
            dao(accountId)?.updateHadBalance(accountId, address, hadBalance)
        }

    suspend fun getAddressesForBalanceCheck(accountId: String): List<ZcashSingleUseAddress> =
        withContext(dispatcherProvider.io) {
            dao(accountId)?.getAddressesWithoutBalance(accountId).orEmpty()
        }

    suspend fun getAllAddresses(accountId: String): List<ZcashSingleUseAddress> =
        withContext(dispatcherProvider.io) {
            dao(accountId)?.getAllAddresses(accountId).orEmpty()
        }

    suspend fun deleteAccountAddresses(accountId: String) = withContext(dispatcherProvider.io) {
        dao(accountId)?.deleteByAccount(accountId)
    }
}
