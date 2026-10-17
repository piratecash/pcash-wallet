package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.entities.SyncerState
import io.mockk.every
import io.mockk.mockk
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopVaultTest {

    private val fixture = DesktopVaultFixture()
    private val vault = fixture.vault()

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun unlock_samePasswordAsCreate_opensTheDatabaseThatWasWritten() = runTest {
        assertEquals(CreateOutcome.SUCCESS, vault.create(password()))
        unlockedSession().database.syncerStateDao().insert(SyncerState("height", "42"))

        vault.lock()
        assertEquals(DesktopVaultState.Locked, vault.state.value)
        assertEquals(UnlockOutcome.Success, vault.unlock(password()))

        assertEquals("42", unlockedSession().database.syncerStateDao().get("height")?.value)
    }

    @Test
    fun unlock_wrongPassword_reportsAttemptsLeftAndSuccessDropsTheCounter() = runTest {
        vault.create(password())
        vault.lock()

        assertEquals(UnlockOutcome.WrongPassword(4), vault.unlock(password(NEW_PASSWORD)))
        assertEquals(UnlockOutcome.WrongPassword(3), vault.unlock(password(NEW_PASSWORD)))
        assertEquals(UnlockOutcome.Success, vault.unlock(password()))

        assertNull(fixture.lockoutStorage.failedAttempts)
    }

    @Test
    fun unlock_fifthWrongPassword_locksOutWithoutCheckingFurtherPasswords() = runTest {
        vault.create(password())
        vault.lock()
        repeat(4) { vault.unlock(password(NEW_PASSWORD)) }

        val lockedOut = assertIs<UnlockOutcome.LockedOut>(vault.unlock(password(NEW_PASSWORD)))
        assertTrue(lockedOut.until.after(Date()))
        assertTrue(assertNotNull(vault.lockedOutUntil()).after(Date()))
        // The correct password is refused too while the lockout lasts.
        assertIs<UnlockOutcome.LockedOut>(vault.unlock(password()))
        assertEquals(DesktopVaultState.Locked, vault.state.value)
    }

    @Test
    fun unlock_corruptedSlotFile_returnsCorrupted() = runTest {
        vault.create(password())
        vault.lock()
        fixture.files.vaultFile.writeBytes(byteArrayOf(1, 2, 3))

        assertEquals(UnlockOutcome.Corrupted, vault.unlock(password()))
        assertEquals(DesktopVaultState.Locked, vault.state.value)
    }

    @Test
    fun unlock_databaseCannotBeOpened_returnsStorageUnavailableAndStaysLocked() = runTest {
        vault.create(password())
        vault.lock()
        val failingVault = fixture.vault(failingDriverFactory())

        assertEquals(UnlockOutcome.StorageUnavailable, failingVault.unlock(password()))

        assertEquals(DesktopVaultState.Locked, failingVault.state.value)
    }

    @Test
    fun unlock_slotFileCannotBeRead_returnsFailedAndStaysLocked() = runTest {
        vault.create(password())
        vault.lock()
        val path = fixture.files.vaultFile.toPath()
        assumeTrue(Files.getFileAttributeView(path, PosixFileAttributeView::class.java) != null)
        Files.setPosixFilePermissions(path, emptySet())

        try {
            assertEquals(UnlockOutcome.Failed, vault.unlock(password()))
            assertEquals(DesktopVaultState.Locked, vault.state.value)
        } finally {
            Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }

    @Test
    fun create_pressedTwice_keepsTheSessionOfTheFirstPress() = runTest {
        vault.create(password())
        val session = unlockedSession()
        session.database.syncerStateDao().insert(SyncerState("height", "42"))

        assertEquals(CreateOutcome.SUCCESS, vault.create(password(NEW_PASSWORD)))

        assertSame(session, unlockedSession())
        assertEquals("42", session.database.syncerStateDao().get("height")?.value)
        assertTrue(fixture.files.vaultFile.exists())
    }

    @Test
    fun create_existingSlotFileAndNoVault_locksWithoutDeletingAnything() = runTest {
        fixture.files.vaultFile.writeBytes(byteArrayOf(7))
        fixture.files.databaseFile.writeBytes(byteArrayOf(8))

        assertEquals(CreateOutcome.SUCCESS, vault.create(password()))

        assertEquals(DesktopVaultState.Locked, vault.state.value)
        assertContentEquals(byteArrayOf(7), fixture.files.vaultFile.readBytes())
        assertContentEquals(byteArrayOf(8), fixture.files.databaseFile.readBytes())
    }

    @Test
    fun create_residualDatabaseCannotBeDeleted_failsBeforeCreatingTheSlot() = runTest {
        // A non-empty directory makes File.delete fail on every OS.
        File(fixture.files.databaseFile, "content").apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(1))

        assertEquals(CreateOutcome.FAILED, vault.create(password()))

        assertEquals(DesktopVaultState.NoVault, vault.state.value)
        assertFalse(fixture.files.vaultFile.exists())
    }

    @Test
    fun create_databaseCannotBeOpened_locksAndReportsFailure() = runTest {
        val failingVault = fixture.vault(failingDriverFactory())

        assertEquals(CreateOutcome.FAILED, failingVault.create(password()))

        assertEquals(DesktopVaultState.Locked, failingVault.state.value)
        assertTrue(fixture.files.vaultFile.exists())
    }

    @Test
    fun create_nativeLibraryFailsToLoad_locksAndReportsFailure() = runTest {
        val failingVault = fixture.vault(nativeLoadFailureDriverFactory())

        assertEquals(CreateOutcome.FAILED, failingVault.create(password()))

        assertEquals(DesktopVaultState.Locked, failingVault.state.value)
    }

    @Test
    fun create_slotFileCannotBeWritten_returnsFailedAndKeepsNoVault() = runTest {
        val directory = fixture.dataDirectory.toPath()
        assumeTrue(Files.getFileAttributeView(directory, PosixFileAttributeView::class.java) != null)
        val permissions = Files.getPosixFilePermissions(directory)
        Files.setPosixFilePermissions(
            directory,
            setOf(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
        )

        try {
            assertEquals(CreateOutcome.FAILED, vault.create(password()))

            assertEquals(DesktopVaultState.NoVault, vault.state.value)
            assertFalse(fixture.files.vaultFile.exists())
        } finally {
            Files.setPosixFilePermissions(directory, permissions)
        }
    }

    @Test
    fun changePassword_correctOldPassword_newPasswordOpensTheSameData() = runTest {
        vault.create(password())
        unlockedSession().database.syncerStateDao().insert(SyncerState("height", "42"))

        assertEquals(ChangePasswordOutcome.SUCCESS, vault.changePassword(password(), password(NEW_PASSWORD)))
        assertIs<DesktopVaultState.Unlocked>(vault.state.value)
        vault.lock()

        assertEquals(UnlockOutcome.WrongPassword(4), vault.unlock(password()))
        assertEquals(UnlockOutcome.Success, vault.unlock(password(NEW_PASSWORD)))
        assertEquals("42", unlockedSession().database.syncerStateDao().get("height")?.value)
    }

    @Test
    fun changePassword_wrongOldPassword_isRejectedAndKeepsTheOldPassword() = runTest {
        vault.create(password())
        val session = unlockedSession()

        val outcome = vault.changePassword(password(NEW_PASSWORD), password("Third-password3"))

        assertEquals(ChangePasswordOutcome.WRONG_PASSWORD, outcome)
        assertSame(session, unlockedSession())
        vault.lock()
        assertEquals(UnlockOutcome.Success, vault.unlock(password()))
    }

    @Test
    fun changePassword_corruptedSlotFile_returnsCorruptedAndKeepsTheSessionOpen() = runTest {
        vault.create(password())
        val session = unlockedSession()
        fixture.files.vaultFile.writeBytes(byteArrayOf(1, 2, 3))

        assertEquals(ChangePasswordOutcome.CORRUPTED, vault.changePassword(password(), password(NEW_PASSWORD)))

        assertSame(session, unlockedSession())
        assertNotNull(session.kitDatabaseKey("btc", "account"))
    }

    @Test
    fun changePassword_slotFileCannotBeRewritten_failsAndTheOldPasswordStillWorks() = runTest {
        vault.create(password())
        val orphan = File(fixture.dataDirectory, "vault.pcv.x.tmp")
        File(orphan, "content").apply { parentFile.mkdirs() }.writeBytes(byteArrayOf(1))

        assertEquals(ChangePasswordOutcome.FAILED, vault.changePassword(password(), password(NEW_PASSWORD)))

        vault.lock()
        assertEquals(UnlockOutcome.Success, vault.unlock(password()))
    }

    @Test
    fun kitDatabaseKey_sameKitAndAccount_isDeterministicAndNullAfterLock() = runTest {
        vault.create(password())
        val session = unlockedSession()

        val key = assertNotNull(session.kitDatabaseKey("bitcoin", "account-1"))
        assertContentEquals(key, session.kitDatabaseKey("bitcoin", "account-1"))
        assertFalse(key.contentEquals(session.kitDatabaseKey("litecoin", "account-1")))
        assertFalse(key.contentEquals(session.kitDatabaseKey("bitcoin", "account-2")))

        vault.lock()
        assertNull(session.kitDatabaseKey("bitcoin", "account-1"))
    }

    private fun unlockedSession(): DesktopStorageSession =
        assertIs<DesktopVaultState.Unlocked>(vault.state.value).session

    private fun failingDriverFactory() = driverFactoryFailingWith(IOException("no space left on device"))

    private fun nativeLoadFailureDriverFactory() =
        driverFactoryFailingWith(ExceptionInInitializerError(UnsatisfiedLinkError("no sqlcipher_jni")))

    private fun driverFactoryFailingWith(failure: Throwable) = object : SqlCipherDriverFactory {
        override fun create(key: ByteArray): SqlCipherDriver = mockk(relaxed = true) {
            every { open(any()) } throws failure
        }
    }
}
