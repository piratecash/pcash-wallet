package cash.p.terminal.core.managers

import android.content.Context
import io.horizontalsystems.bitcoincore.core.IConnectionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BitcoinKitEnvironment(
    val dataDir: String,
    val mwebDataDir: String,
    val databaseKey: ByteArray,
    val connectionManager: IConnectionManager,
)

class BitcoinKitDatabaseManager(
    context: Context,
    private val keyProvider: BitcoinKitDatabaseKeyProvider,
    private val operations: BitcoinKitDatabaseOperations,
    private val connectionManager: IConnectionManager,
) {
    private val mutex = Mutex()
    private val preparedAccountIds = mutableSetOf<String>()
    private val dataDir = requireNotNull(context.getDatabasePath(DATABASE_PATH_MARKER).parent)
    private val mwebDataDir = context.noBackupFilesDir.absolutePath

    suspend fun prepare(accountId: String): BitcoinKitEnvironment = mutex.withLock {
        val databaseKey = awaitDatabaseKey(accountId)
        if (accountId !in preparedAccountIds) {
            operations.migrate(dataDir, accountId, databaseKey)
            preparedAccountIds.add(accountId)
        }
        BitcoinKitEnvironment(dataDir, mwebDataDir, databaseKey, connectionManager)
    }

    suspend fun clear(accountId: String) = mutex.withLock {
        preparedAccountIds.remove(accountId)
        operations.clear(dataDir, mwebDataDir, accountId)
        keyProvider.remove(accountId)
    }

    suspend fun clearMweb(accountId: String) = mutex.withLock {
        operations.clearMweb(dataDir, mwebDataDir, accountId)
    }

    private suspend fun awaitDatabaseKey(accountId: String): ByteArray {
        while (true) {
            try {
                return keyProvider.keyFor(accountId)
            } catch (_: BitcoinKitDatabaseKeyLockedException) {
                delay(KEYSTORE_RETRY_DELAY_MS)
            }
        }
    }

    private companion object {
        const val DATABASE_PATH_MARKER = "bitcoin-kit-path"
        const val KEYSTORE_RETRY_DELAY_MS = 500L
    }
}
