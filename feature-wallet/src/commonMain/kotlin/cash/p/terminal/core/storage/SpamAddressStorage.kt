package cash.p.terminal.core.storage

import cash.p.terminal.entities.SpamAddress
import cash.p.terminal.entities.SpamScanState
import io.horizontalsystems.core.entities.BlockchainType

class SpamAddressStorage(
    private val router: StorageRouter
) {
    fun isSpam(hash: ByteArray): Boolean =
        router.firstInAccounts { spamAddressDao().getByTransaction(hash) } != null

    fun findByAddress(address: String): SpamAddress? =
        router.firstInAccounts { spamAddressDao().getByAddress(address) }

    fun save(accountId: String, spamAddresses: List<SpamAddress>) {
        router.account(accountId)?.spamAddressDao()?.insertAll(spamAddresses)
    }

    fun save(spamScanState: SpamScanState) {
        router.account(spamScanState.accountId)?.spamAddressDao()?.insert(spamScanState)
    }

    fun getSpamScanState(blockchainType: BlockchainType, accountUid: String): SpamScanState? =
        router.account(accountUid)?.spamAddressDao()?.getSpamScanState(blockchainType, accountUid)
}
