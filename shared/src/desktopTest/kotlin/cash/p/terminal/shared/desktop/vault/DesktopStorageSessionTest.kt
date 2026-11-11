package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.shared.desktop.session.anyFileContains
import cash.p.terminal.vault.EnvelopePayload
import cash.p.terminal.vault.PasswordVault
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
import java.io.File
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
private const val ACCOUNT_ID = "account-1"

class DesktopStorageSessionTest {

    private val fixture = DesktopVaultFixture()
    private val vault = fixture.vault()

    @AfterTest
    fun tearDown() {
        stopKoin()
        fixture.tearDown()
    }

    @Test
    fun open_accountStoreWithoutItsKey_failsAndNoFileHoldsAPlaintextSecret() = runTest {
        startEncryptionGraph()
        vault.create(password())
        val router = DesktopStorageRouter(vault)
        router.prepareAccountStore(ACCOUNT_ID)
        checkNotNull(router.account(ACCOUNT_ID)).accountsDao().insert(accountRecord())
        vault.close()

        assertFalse(fixture.dataDirectory.anyFileContains(MNEMONIC_WORD))
        assertFalse(fixture.dataDirectory.anyFileContains(PASSPHRASE))
        val accountStore = fixture.files.kitsDirectory.walkTopDown().first { it.isFile && !it.name.endsWith(".lck") }
        assertFailsWith<Exception> { readAccountsWith(accountStore, ByteArray(KEY_SIZE) { 9 }) }
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
        val payload = EnvelopePayload(0, ByteArray(KEY_SIZE) { 1 }, ByteArray(KEY_SIZE) { 2 })
        val vaultSession = mockk<VaultSession>(relaxed = true) {
            every { envelopeIndex } returns 0
            every { payload() } returns payload
        }
        val driver = mockk<SqlCipherDriver>(relaxed = true) {
            every { open(any()) } throws failure
        }
        val environment = StorageEnvironment(
            fixture.files,
            PasswordVault(fixture.files.vaultFile),
            driverFactory(driver),
            NoStorageCheckpoints,
            object : VaultLock {
                override fun <T> exclusive(block: () -> T): T = block()
            },
        )

        val thrown = assertFailsWith<Throwable> { DesktopStorageSession.open(vaultSession, environment) }

        // Coroutine stack-trace recovery may rethrow a copy that keeps the original as its cause.
        assertTrue(generateSequence(thrown, Throwable::cause).any { it === failure })
        verify { driver.close() }
        verify { vaultSession.close() }
        assertTrue(payload.slotKey.all { it == 0.toByte() })
        assertTrue(payload.sharedKey.all { it == 0.toByte() })
    }

    private fun readAccountsWith(file: File, key: ByteArray) =
        SqlCipherDriver(key).use { driver ->
            driver.open(file.absolutePath).use { connection ->
                connection.prepare("SELECT name FROM AccountRecord").use { statement -> statement.step() }
            }
        }

    private fun accountRecord() = AccountRecord(
        id = ACCOUNT_ID,
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
