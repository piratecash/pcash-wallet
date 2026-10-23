package cash.p.terminal.shared.desktop.vault

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.AppDatabaseConstructor
import cash.p.terminal.core.storage.migrations.Migration_118_119
import cash.p.terminal.core.storage.migrations.Migration_119_120
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.PremiumDatabaseConstructor
import cash.p.terminal.vault.Hkdf
import cash.p.terminal.vault.VaultSession
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.security.MessageDigest

/**
 * Owns the unlocked databases and the keys derived from the master key.
 * [close] takes the databases down and wipes every key, the vault session included.
 */
class DesktopStorageSession private constructor(
    private val vaultSession: VaultSession,
    private val drivers: List<SqlCipherDriver>,
    internal val database: AppDatabase,
    internal val premiumDatabase: PremiumDatabase,
    private val secretKey: ByteArray,
    private val kitWrappingKey: ByteArray,
) {
    @Volatile
    private var closed = false

    /** Derived per kit and account, so no kit key is ever stored; null once closed. */
    fun kitDatabaseKey(kitName: String, accountId: String): ByteArray? =
        if (closed) null else deriveKitKey(kitWrappingKey, kitName, accountId)

    internal fun secretFieldsKey(): ByteArray? = if (closed) null else secretKey

    /** Whether [vaultSession] carries the master key this storage was opened with. */
    internal fun isOpenedBy(vaultSession: VaultSession): Boolean {
        val candidate = vaultSession.secretFieldsKey() ?: return false
        return try {
            !closed && MessageDigest.isEqual(candidate, secretKey)
        } finally {
            candidate.fill(0)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        closeInOrder(listOf(premiumDatabase, database), drivers, listOf(secretKey, kitWrappingKey), vaultSession)
    }

    companion object {
        private const val KEY_SIZE = 32
        private const val PREMIUM_DATABASE_FILE = "premium.db"
        private const val PREMIUM_KIT_NAME = "premium-db"

        /**
         * Opens the app database right away, so a key that does not fit the file fails here and not at
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
            var premiumDriver: SqlCipherDriver? = null
            var premiumDatabase: PremiumDatabase? = null
            var secretKey: ByteArray? = null
            var kitWrappingKey: ByteArray? = null
            try {
                secretKey = checkNotNull(vaultSession.secretFieldsKey())
                kitWrappingKey = checkNotNull(vaultSession.kitKeysWrappingKey())
                driver = driverFactory.create(databaseKey)
                database = buildDatabase(
                    files.databaseFile,
                    driver,
                    AppDatabaseConstructor::initialize,
                    Migration_118_119,
                    Migration_119_120,
                )
                // Opening is what reveals a key that does not fit the file.
                database.syncerStateDao().get("")
                premiumDriver = createPremiumDriver(driverFactory, kitWrappingKey)
                premiumDatabase = buildDatabase(
                    File(files.kitsDirectory.apply { mkdirs() }, PREMIUM_DATABASE_FILE),
                    premiumDriver,
                    PremiumDatabaseConstructor::initialize,
                )
                return DesktopStorageSession(
                    vaultSession, listOf(premiumDriver, driver), database, premiumDatabase, secretKey, kitWrappingKey
                )
            } catch (e: Throwable) {
                // An Error from the SQLCipher native load must not leak an open database either.
                closeInOrder(
                    listOf(premiumDatabase, database),
                    listOf(premiumDriver, driver),
                    listOf(secretKey, kitWrappingKey),
                    vaultSession,
                )
                throw e
            } finally {
                databaseKey.fill(0)
            }
        }

        /** The premium cache lives as long as the vault, keyed like a kit database. */
        private fun createPremiumDriver(
            driverFactory: SqlCipherDriverFactory,
            kitWrappingKey: ByteArray,
        ): SqlCipherDriver {
            val key = deriveKitKey(kitWrappingKey, PREMIUM_KIT_NAME, "")
            try {
                return driverFactory.create(key)
            } finally {
                key.fill(0)
            }
        }

        private fun deriveKitKey(kitWrappingKey: ByteArray, kitName: String, accountId: String): ByteArray =
            Hkdf.expand(kitWrappingKey, "kit-db/v1/$kitName/$accountId".toByteArray(Charsets.UTF_8), KEY_SIZE)

        private inline fun <reified T : RoomDatabase> buildDatabase(
            file: File,
            driver: SqlCipherDriver,
            noinline factory: () -> T,
            vararg migrations: Migration,
        ): T =
            Room.databaseBuilder<T>(name = file.absolutePath, factory = factory)
                .setDriver(driver)
                .addMigrations(*migrations)
                // A blocking DAO nested in a transaction must reach Room's `useConnection` undispatched.
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        private fun closeInOrder(
            databases: List<RoomDatabase?>,
            drivers: List<SqlCipherDriver?>,
            keys: List<ByteArray?>,
            vaultSession: VaultSession,
        ) {
            databases.forEach { tryOrNull { it?.close() } }
            drivers.forEach { tryOrNull { it?.close() } }
            keys.forEach { it?.fill(0) }
            vaultSession.close()
        }
    }
}
