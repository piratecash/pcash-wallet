package cash.p.terminal.shared.desktop.vault

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
import kotlin.test.AfterTest
import kotlin.test.Test
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

    private fun startEncryptionGraph() {
        startKoin {
            modules(module { single<IEncryptionManager> { DesktopSecretFieldsEncryption(vault) } })
        }
    }

    private companion object {
        const val KEY_SIZE = 32
    }
}
