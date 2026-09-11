package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.canonicalTransactionHash
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single source of truth for "this transaction is an Orchard -> Ironwood migration". The mined
 * record and the pending one are classified here, so the two can never disagree.
 */
class ZcashIronwoodMigrationRegistry(private val localStorage: ILocalStorage) {

    private val mutex = Mutex()

    suspend fun remember(accountId: String, transactionHashes: List<String>) {
        if (transactionHashes.isEmpty()) return
        mutex.withLock {
            localStorage.zcashIronwoodMigrationTxIds = localStorage.zcashIronwoodMigrationTxIds +
                transactionHashes.map { key(accountId, it) }
        }
    }

    /**
     * Read from storage on every check instead of caching: the same transaction is also listed by
     * the sibling Zcash adapters of this account, which never write the migration ids themselves.
     */
    fun contains(accountId: String, transactionHashHex: String) =
        key(accountId, transactionHashHex) in localStorage.zcashIronwoodMigrationTxIds

    private fun key(accountId: String, transactionHashHex: String) =
        "$accountId:" + transactionHashHex.canonicalTransactionHash()
}
