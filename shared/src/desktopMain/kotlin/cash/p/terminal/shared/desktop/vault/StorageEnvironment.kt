package cash.p.terminal.shared.desktop.vault

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import cash.p.terminal.vault.EnvelopePayload
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.SecureRandom

/** Serializes everything that changes the safes or `vault.pcv`. */
internal interface VaultLock {
    fun <T> exclusive(block: () -> T): T
}

internal class StorageEnvironment(
    val files: DesktopVaultFiles,
    val passwordVault: PasswordVault,
    val driverFactory: SqlCipherDriverFactory,
    val checkpoints: StorageCheckpoints,
    val lock: VaultLock,
) {
    val slots = VaultSlots(files)
    private val random = SecureRandom()

    fun randomKey(): ByteArray = ByteArray(EnvelopePayload.KEY_SIZE).also(random::nextBytes)

    fun randomElement(pool: Set<Int>): Int = pool.elementAt(random.nextInt(pool.size))

    fun shuffled(pool: Set<Int>): List<Int> = pool.shuffled(random)

    /** The driver keeps its own copy of [key]. */
    inline fun <reified T : RoomDatabase> openDatabase(
        file: File,
        key: ByteArray,
        noinline factory: () -> T,
    ): EncryptedDatabase<T> {
        val driver = driverFactory.create(key)
        val room = try {
            Room.databaseBuilder<T>(name = file.absolutePath, factory = factory)
                .setDriver(driver)
                // A blocking DAO nested in a transaction must reach Room's `useConnection` undispatched.
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        } catch (e: Throwable) {
            driver.close()
            throw e
        }
        return EncryptedDatabase(room, driver)
    }
}

internal class EncryptedDatabase<T : RoomDatabase>(val room: T, private val driver: SqlCipherDriver) : AutoCloseable {

    /** Room opens lazily; this reveals a key that does not fit and creates a missing file right away. */
    fun openNow() {
        runBlocking { room.useReaderConnection { it.usePrepared("SELECT 1") { statement -> statement.step() } } }
    }

    override fun close() {
        tryOrNull { room.close() }
        tryOrNull { driver.close() }
    }
}
