package cash.p.terminal.core.managers

import io.horizontalsystems.bitcoincore.core.IConnectionManager
import io.horizontalsystems.databasenaming.DatabaseNameResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BitcoinKitEnvironment(
    val dataDir: String,
    val mwebDataDir: String,
    val databaseKey: ByteArray,
    val connectionManager: IConnectionManager,
    val nameResolver: DatabaseNameResolver,
    val mwebDebugLogDisabled: Boolean,
)

class BitcoinKitDatabaseManager(
    private val platformContexts: KitPlatformContexts,
    private val storageNaming: KitStorageNaming,
    private val keyProvider: BitcoinKitDatabaseKeyProvider,
    private val operations: BitcoinKitDatabaseOperations,
    private val connectionManager: IConnectionManager,
) {
    private val mutex = Mutex()
    private val preparedAccountIds = mutableSetOf<String>()

    suspend fun prepare(accountId: String): BitcoinKitEnvironment = mutex.withLock {
        val databaseKey = keyProvider.awaitKey(accountId)
        val dataDir = dataDir(accountId)
        val nameResolver = storageNaming.resolverFor(accountId)
        if (accountId !in preparedAccountIds) {
            operations.migrate(dataDir, accountId, databaseKey, nameResolver)
            preparedAccountIds.add(accountId)
        }
        BitcoinKitEnvironment(
            dataDir,
            mwebDataDir(accountId),
            databaseKey,
            connectionManager,
            nameResolver,
            platformContexts.mwebDebugLogDisabled,
        )
    }

    /** An account out of view is prepared anew if it returns, as after a restart. */
    suspend fun retainAccounts(accountIds: Set<String>) = mutex.withLock {
        preparedAccountIds.retainAll(accountIds)
    }

    suspend fun clear(accountId: String) = mutex.withLock {
        preparedAccountIds.remove(accountId)
        operations.clear(dataDir(accountId), mwebDataDir(accountId), accountId, storageNaming.resolverFor(accountId))
        keyProvider.remove(accountId)
    }

    suspend fun clearMweb(accountId: String) = mutex.withLock {
        operations.clearMweb(
            dataDir(accountId),
            mwebDataDir(accountId),
            accountId,
            storageNaming.resolverFor(accountId),
        )
    }

    private fun dataDir(accountId: String) = platformContexts.bitcoinDatabaseDir(accountId).path

    private fun mwebDataDir(accountId: String) = platformContexts.bitcoinMwebDir(accountId).absolutePath
}
