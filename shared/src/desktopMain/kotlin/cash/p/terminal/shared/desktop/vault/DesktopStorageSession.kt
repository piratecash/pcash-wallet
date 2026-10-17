package cash.p.terminal.shared.desktop.vault

import androidx.room.Room
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.AppDatabaseConstructor
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.vault.Hkdf
import cash.p.terminal.vault.VaultSession
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Owns the unlocked database and the keys derived from the master key.
 * [close] takes the database down and wipes every key, the vault session included.
 */
class DesktopStorageSession private constructor(
    private val vaultSession: VaultSession,
    private val driver: SqlCipherDriver,
    internal val database: AppDatabase,
    private val secretKey: ByteArray,
    private val kitWrappingKey: ByteArray,
) {
    @Volatile
    private var closed = false

    /** Derived per kit and account, so no kit key is ever stored; null once closed. */
    fun kitDatabaseKey(kitName: String, accountId: String): ByteArray? =
        if (closed) null else Hkdf.expand(kitWrappingKey, kitKeyInfo(kitName, accountId), KEY_SIZE)

    internal fun secretFieldsKey(): ByteArray? = if (closed) null else secretKey

    fun close() {
        if (closed) return
        closed = true
        closeInOrder(database, driver, listOf(secretKey, kitWrappingKey), vaultSession)
    }

    private fun kitKeyInfo(kitName: String, accountId: String): ByteArray =
        "kit-db/v1/$kitName/$accountId".toByteArray(Charsets.UTF_8)

    companion object {
        private const val KEY_SIZE = 32

        /**
         * Opens the database right away, so a key that does not fit the file fails here and not at
         * the first query later. Cleans up after itself on any failure and rethrows.
         */
        fun open(
            vaultSession: VaultSession,
            files: DesktopVaultFiles,
            driverFactory: SqlCipherDriverFactory,
        ): DesktopStorageSession {
            val databaseKey = checkNotNull(vaultSession.appDatabaseKey()) { "Vault session is closed" }
            var driver: SqlCipherDriver? = null
            var database: AppDatabase? = null
            var secretKey: ByteArray? = null
            var kitWrappingKey: ByteArray? = null
            try {
                secretKey = checkNotNull(vaultSession.secretFieldsKey())
                kitWrappingKey = checkNotNull(vaultSession.kitKeysWrappingKey())
                driver = driverFactory.create(databaseKey)
                database = buildDatabase(files.databaseFile, driver)
                // Opening is what reveals a key that does not fit the file.
                database.syncerStateDao().get("")
                return DesktopStorageSession(vaultSession, driver, database, secretKey, kitWrappingKey)
            } catch (e: Throwable) {
                // An Error from the SQLCipher native load must not leak an open database either.
                closeInOrder(database, driver, listOf(secretKey, kitWrappingKey), vaultSession)
                throw e
            } finally {
                databaseKey.fill(0)
            }
        }

        private fun buildDatabase(file: File, driver: SqlCipherDriver): AppDatabase =
            Room.databaseBuilder<AppDatabase>(
                name = file.absolutePath,
                factory = AppDatabaseConstructor::initialize,
            )
                .setDriver(driver)
                // A blocking DAO nested in a transaction must reach Room's `useConnection` undispatched.
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        private fun closeInOrder(
            database: AppDatabase?,
            driver: SqlCipherDriver?,
            keys: List<ByteArray?>,
            vaultSession: VaultSession,
        ) {
            tryOrNull { database?.close() }
            tryOrNull { driver?.close() }
            keys.forEach { it?.fill(0) }
            vaultSession.close()
        }
    }
}
