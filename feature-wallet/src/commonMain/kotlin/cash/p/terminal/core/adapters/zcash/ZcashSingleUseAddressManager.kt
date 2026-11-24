package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.entities.ZcashSingleUseAddress

class ZcashSingleUseAddressManager(
    private val storage: ZcashSingleUseAddressStorage,
    private val accountId: String
) {

    /** Reserved, not yet sent to any provider: `useCount` counts the orders it was sent in. */
    suspend fun saveNewAddress(address: String) {
        storage.saveAddress(
            ZcashSingleUseAddress(
                accountId = accountId,
                address = address,
                useCount = 0,
                hadBalance = false
            )
        )
    }

    suspend fun updateAddressBalance(address: String, hasBalance: Boolean) {
        if (hasBalance) {
            storage.updateHadBalance(accountId, address, true)
        }
    }

    suspend fun getAddressesForBalanceCheck(): List<String> {
        return storage.getAddressesForBalanceCheck(accountId).map { it.address }
    }

    /** Every address reserved for this account, used or not, for use as a fresh-address exclusion list. */
    suspend fun getAllAddresses(): List<String> {
        return storage.getAllAddresses(accountId).map { it.address }
    }
}
