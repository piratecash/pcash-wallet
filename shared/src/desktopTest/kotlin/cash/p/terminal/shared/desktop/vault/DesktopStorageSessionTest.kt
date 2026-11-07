package cash.p.terminal.shared.desktop.vault

import androidx.sqlite.execSQL
import cash.p.terminal.entities.StackingCache
import cash.p.terminal.vault.VaultSession
import cash.p.terminal.wallet.entities.AccountRecord
import cash.p.terminal.wallet.entities.SecretList
import cash.p.terminal.wallet.entities.SecretString
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.IOException
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MNEMONIC_WORD = "abandonment-seed-word"
private const val PASSPHRASE = "unrepeatable-passphrase"

class DesktopStorageSessionTest {

    private val fixture = DesktopVaultFixture()
    private val vault = fixture.vault()

    @AfterTest
    fun tearDown() {
        stopKoin()
        fixture.tearDown()
    }

    @Test
    fun open_databaseFileWithoutItsKey_failsAndTheFileHoldsNoPlaintextSecret() = runTest {
        startEncryptionGraph()
        vault.create(password())
        val session = assertIs<DesktopVaultState.Unlocked>(vault.state.value).session
        session.database.accountsDao().insert(accountRecord())
        vault.close()

        val fileBytes = fixture.files.databaseFile.readBytes()
        assertFalse(fileBytes.decodeToString().contains(MNEMONIC_WORD))
        assertFalse(fileBytes.decodeToString().contains(PASSPHRASE))
        assertFailsWith<Exception> { readAccountsWith(ByteArray(KEY_SIZE) { 9 }) }
    }

    @Test
    fun open_version118Database_migratesAndStoresStackingCache() = runTest {
        val keys = mutableListOf<ByteArray>()
        val keyCapturingVault = fixture.vault(driverFactory = object : SqlCipherDriverFactory {
            override fun create(key: ByteArray): SqlCipherDriver =
                DefaultSqlCipherDriverFactory().create(key).also { keys += key.copyOf() }
        })
        keyCapturingVault.create(password())
        keyCapturingVault.close()
        downgradeToVersion118(appDatabaseKey = keys.first())
        val cache = StackingCache(
            coin = "pirate",
            address = "address",
            unpaid = BigDecimal("1.5"),
            totalIncome = BigDecimal("10"),
            nextAccrualAt = 1_000L,
            nextPayoutAt = null,
            cachedBalance = BigDecimal("100"),
            timestamp = 2_000L,
        )

        keyCapturingVault.unlock(password())
        val database = assertIs<DesktopVaultState.Unlocked>(keyCapturingVault.state.value).session.database
        database.stackingCacheDao().save(cache)

        assertEquals(cache, database.stackingCacheDao().get("pirate", "address"))
        keyCapturingVault.close()
    }

    @Test
    fun open_version122DatabaseWithNftTables_dropsNftTablesAndKeepsAccount() = runTest {
        val keys = mutableListOf<ByteArray>()
        val keyCapturingVault = fixture.vault(driverFactory = object : SqlCipherDriverFactory {
            override fun create(key: ByteArray): SqlCipherDriver =
                DefaultSqlCipherDriverFactory().create(key).also { keys += key.copyOf() }
        })
        startEncryptionGraph(keyCapturingVault)
        keyCapturingVault.create(password())
        assertIs<DesktopVaultState.Unlocked>(keyCapturingVault.state.value).session.database
            .accountsDao().insert(accountRecord())
        keyCapturingVault.close()
        downgradeToVersion122WithNftTables(appDatabaseKey = keys.first())

        keyCapturingVault.unlock(password())
        val database = assertIs<DesktopVaultState.Unlocked>(keyCapturingVault.state.value).session.database

        assertEquals(listOf("account-1"), database.accountsDao().getIds())
        keyCapturingVault.close()
        assertEquals(0L, countNftTables(appDatabaseKey = keys.first()))
    }

    @Test
    fun open_driverCannotOpenTheFile_closesDriverKeysAndVaultSession() =
        assertOpenFailureCleansUp(IOException("no space left on device"))

    @Test
    fun open_nativeLibraryFailsToLoad_closesDriverKeysAndVaultSessionAndPropagates() =
        assertOpenFailureCleansUp(ExceptionInInitializerError(UnsatisfiedLinkError("no sqlcipher_jni")))

    @Test
    fun secretFieldsKey_afterClose_returnsNull() = runTest {
        vault.create(password())
        val session = assertIs<DesktopVaultState.Unlocked>(vault.state.value).session

        vault.close()

        assertNull(session.secretFieldsKey())
    }

    private fun assertOpenFailureCleansUp(failure: Throwable) = runTest {
        val databaseKey = ByteArray(KEY_SIZE) { 1 }
        val secretFieldsKey = ByteArray(KEY_SIZE) { 2 }
        val kitKeysWrappingKey = ByteArray(KEY_SIZE) { 3 }
        val vaultSession = mockk<VaultSession>(relaxed = true) {
            every { appDatabaseKey() } returns databaseKey
            every { secretFieldsKey() } returns secretFieldsKey
            every { kitKeysWrappingKey() } returns kitKeysWrappingKey
        }
        val driver = mockk<SqlCipherDriver>(relaxed = true) {
            every { open(any()) } throws failure
        }

        val thrown = assertFailsWith<Throwable> {
            DesktopStorageSession.open(vaultSession, fixture.files, driverFactory(driver))
        }

        // Coroutine stack-trace recovery may rethrow a copy that keeps the original as its cause.
        assertTrue(generateSequence(thrown, Throwable::cause).any { it === failure })
        verify { driver.close() }
        verify { vaultSession.close() }
        assertTrue(databaseKey.all { it == 0.toByte() })
        assertTrue(secretFieldsKey.all { it == 0.toByte() })
        assertTrue(kitKeysWrappingKey.all { it == 0.toByte() })
    }

    private fun readAccountsWith(key: ByteArray) =
        SqlCipherDriver(key).use { driver ->
            driver.open(fixture.files.databaseFile.absolutePath).use { connection ->
                connection.prepare("SELECT name FROM AccountRecord").use { statement -> statement.step() }
            }
        }

    private fun downgradeToVersion118(appDatabaseKey: ByteArray) =
        SqlCipherDriver(appDatabaseKey).use { driver ->
            driver.open(fixture.files.databaseFile.absolutePath).use { connection ->
                connection.execSQL("DROP TABLE StackingCache")
                connection.execSQL("ALTER TABLE SwapProviderTransaction DROP COLUMN receivedAt")
                connection.execSQL("PRAGMA user_version = 118")
            }
        }

    private fun downgradeToVersion122WithNftTables(appDatabaseKey: ByteArray) =
        SqlCipherDriver(appDatabaseKey).use { driver ->
            driver.open(fixture.files.databaseFile.absolutePath).use { connection ->
                // Column layout is irrelevant: the migration drops the tables by name.
                NFT_TABLES.forEach { table ->
                    connection.execSQL("CREATE TABLE `$table` (`id` TEXT NOT NULL)")
                    connection.execSQL("INSERT INTO `$table` VALUES ('row')")
                }
                connection.execSQL("PRAGMA user_version = 122")
            }
        }

    private fun countNftTables(appDatabaseKey: ByteArray): Long =
        SqlCipherDriver(appDatabaseKey).use { driver ->
            driver.open(fixture.files.databaseFile.absolutePath).use { connection ->
                connection.prepare(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE 'Nft%'"
                ).use { statement ->
                    statement.step()
                    statement.getLong(0)
                }
            }
        }

    private fun accountRecord() = AccountRecord(
        id = "account-1",
        name = "Main",
        type = "mnemonic",
        origin = "Created",
        isBackedUp = false,
        isFileBackedUp = false,
        words = SecretList(listOf(MNEMONIC_WORD)),
        passphrase = SecretString(PASSPHRASE),
        key = null,
        level = 0,
    )

    private fun driverFactory(driver: SqlCipherDriver) = object : SqlCipherDriverFactory {
        override fun create(key: ByteArray): SqlCipherDriver = driver
    }

    private fun startEncryptionGraph(encryptingVault: DesktopVault = vault) {
        startKoin {
            modules(module { single<IEncryptionManager> { DesktopSecretFieldsEncryption(encryptingVault) } })
        }
    }

    private companion object {
        const val KEY_SIZE = 32

        val NFT_TABLES = listOf(
            "NftCollectionRecord", "NftAssetRecord", "NftMetadataSyncRecord", "NftAssetBriefMetadataRecord",
        )
    }
}
