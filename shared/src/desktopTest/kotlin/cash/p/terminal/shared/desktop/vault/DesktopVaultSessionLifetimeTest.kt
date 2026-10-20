package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.entities.SyncerState
import cash.p.terminal.vault.CreateResult
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.vault.UnlockResult
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The lock covers the UI only: the storage session lives from the first unlock until wipe or quit. */
class DesktopVaultSessionLifetimeTest {

    private val fixture = DesktopVaultFixture()
    private val driverFactory = CountingDriverFactory()
    private val vault = fixture.vault(driverFactory = driverFactory)

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun lock_unlockedVault_keepsTheDatabaseKitKeysAndSecretFieldsWorking() = runTest {
        vault.create(password())
        val session = assertNotNull(vault.openSession())

        vault.lock()

        assertEquals(DesktopVaultState.Locked, vault.state.value)
        assertSame(session, vault.openSession())
        session.database.syncerStateDao().insert(SyncerState("height", "42"))
        assertEquals("42", session.database.syncerStateDao().get("height")?.value)
        assertNotNull(session.kitDatabaseKey("bitcoin", "account-1"))
        val encryption = DesktopSecretFieldsEncryption(vault)
        assertEquals(SECRET, encryption.decrypt(encryption.encrypt(SECRET)))
    }

    @Test
    fun lock_writeStartedBeforeTheLock_completes() = runTest {
        vault.create(password())
        val dao = assertNotNull(vault.openSession()).database.syncerStateDao()
        val lockDone = CompletableDeferred<Unit>()

        val write = async(Dispatchers.IO) {
            lockDone.await()
            dao.insert(SyncerState("height", "42"))
        }
        vault.lock()
        lockDone.complete(Unit)
        write.await()

        assertEquals("42", withContext(Dispatchers.IO) { dao.get("height")?.value })
    }

    @Test
    fun unlock_openSession_checksThePasswordAndKeepsTheSameSession() = runTest {
        val passwordVault = spyk(PasswordVault(fixture.files.vaultFile))
        val vault = fixture.vault(driverFactory = driverFactory, passwordVault = passwordVault)
        val unlockResults = mutableListOf<UnlockResult>()
        every { passwordVault.unlock(any()) } answers { (callOriginal() as UnlockResult).also(unlockResults::add) }
        vault.create(password())
        val session = assertNotNull(vault.openSession())
        val openedDrivers = driverFactory.created
        vault.lock()

        assertEquals(UnlockOutcome.WrongPassword(4), vault.unlock(password(NEW_PASSWORD)))
        assertEquals(UnlockOutcome.Success, vault.unlock(password()))

        assertSame(session, assertIs<DesktopVaultState.Unlocked>(vault.state.value).session)
        assertSame(session, vault.openSession())
        assertEquals(openedDrivers, driverFactory.created, "a second database was opened")
        val checked = assertIs<UnlockResult.Success>(unlockResults.last())
        assertNull(checked.session.appDatabaseKey(), "the vault session of the check stayed open")
        assertNotNull(session.kitDatabaseKey("bitcoin", "account-1"))
    }

    @Test
    fun wipe_openSession_closesItAndLeavesNoSession() = runTest {
        vault.create(password())
        val session = assertNotNull(vault.openSession())
        vault.lock()

        assertEquals(WipeOutcome.SUCCESS, vault.wipe())

        assertNull(vault.openSession())
        assertNull(session.kitDatabaseKey("bitcoin", "account-1"))
        assertNull(session.secretFieldsKey())
    }

    @Test
    fun close_openSession_locksAndClosesIt() = runTest {
        vault.create(password())
        val session = assertNotNull(vault.openSession())

        vault.close()

        assertEquals(DesktopVaultState.Locked, vault.state.value)
        assertNull(vault.openSession())
        assertNull(session.secretFieldsKey())
    }

    @Test
    fun openSession_freshStartWithAnExistingVault_isNullUntilThePasswordIsEntered() = runTest {
        vault.create(password())
        vault.close()
        val restarted = fixture.vault(driverFactory = driverFactory)

        assertEquals(DesktopVaultState.Locked, restarted.state.value)
        assertNull(restarted.openSession())

        assertEquals(UnlockOutcome.Success, restarted.unlock(password()))
        assertNotNull(restarted.openSession())
    }

    @Test
    fun verifyPassword_wrongPassword_reportsAttemptsLeftAndCountsIntoTheUnlockLockout() = runTest {
        vault.create(password())
        val session = vault.openSession()

        assertEquals(UnlockOutcome.WrongPassword(4), vault.verifyPassword(password(NEW_PASSWORD)))
        repeat(3) { vault.verifyPassword(password(NEW_PASSWORD)) }
        assertIs<UnlockOutcome.LockedOut>(vault.verifyPassword(password(NEW_PASSWORD)))

        vault.lock()
        assertIs<UnlockOutcome.LockedOut>(vault.unlock(password()))
        assertSame(session, vault.openSession())
    }

    @Test
    fun verifyPassword_correctPassword_succeedsDropsTheCounterAndWipesThePassword() = runTest {
        vault.create(password())
        val session = vault.openSession()
        vault.verifyPassword(password(NEW_PASSWORD))
        val entered = password()

        assertEquals(UnlockOutcome.Success, vault.verifyPassword(entered))

        assertNull(fixture.lockoutStorage.failedAttempts)
        assertEquals(String(CharArray(entered.size)), String(entered))
        assertSame(session, vault.openSession())
        assertIs<DesktopVaultState.Unlocked>(vault.state.value)
    }

    @Test
    fun unlock_slotFileOfAnotherVault_staysLockedAndReportsCorrupted() = runTest {
        vault.create(password())
        val session = vault.openSession()
        val ownSlot = fixture.files.vaultFile.readBytes()
        replaceSlotWithAnotherVault()

        assertEquals(UnlockOutcome.Corrupted, vault.verifyPassword(password(NEW_PASSWORD)))
        vault.lock()
        assertEquals(UnlockOutcome.Corrupted, vault.unlock(password(NEW_PASSWORD)))
        assertEquals(DesktopVaultState.Locked, vault.state.value)

        fixture.files.vaultFile.writeBytes(ownSlot)
        assertEquals(UnlockOutcome.Success, vault.unlock(password()))
        assertSame(session, vault.openSession())
    }

    private fun replaceSlotWithAnotherVault() {
        val otherSlot = File(File(fixture.dataDirectory, "other").apply { mkdirs() }, "vault")
        assertIs<CreateResult.Created>(PasswordVault(otherSlot).create(password(NEW_PASSWORD))).session.close()
        otherSlot.copyTo(fixture.files.vaultFile, overwrite = true)
    }

    private class CountingDriverFactory : SqlCipherDriverFactory {
        var created = 0

        override fun create(key: ByteArray): SqlCipherDriver {
            created++
            return DefaultSqlCipherDriverFactory().create(key)
        }
    }

    private companion object {
        const val SECRET = "test secret field"
    }
}
