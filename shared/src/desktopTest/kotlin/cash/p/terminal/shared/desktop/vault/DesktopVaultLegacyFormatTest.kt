package cash.p.terminal.shared.desktop.vault

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopVaultLegacyFormatTest {
    private val fixture = DesktopVaultFixture()

    @AfterTest
    fun tearDown() = fixture.tearDown()

    private fun writeVersion1File() = fixture.files.vaultFile.writeBytes("PCKV".toByteArray() + VERSION_1 + ByteArray(64))

    @Test
    fun unlock_version1File_isLegacyFormatWithoutCountingAnAttempt() = runBlocking<Unit> {
        writeVersion1File()
        val vault = fixture.vault()

        assertEquals(UnlockOutcome.LegacyFormat, vault.unlock(password()))

        assertEquals(DesktopVaultState.Locked, vault.state.value)
        assertEquals(null, vault.lockedOutUntil())
        assertEquals(null, fixture.lockoutStorage.failedAttempts)
    }

    @Test
    fun verifyPassword_version1File_isLegacyFormat() = runBlocking<Unit> {
        writeVersion1File()

        assertEquals(UnlockOutcome.LegacyFormat, fixture.vault().verifyPassword(password()))
    }

    @Test
    fun changePassword_version1File_isCorrupted() = runBlocking<Unit> {
        writeVersion1File()

        assertEquals(ChangePasswordOutcome.CORRUPTED, fixture.vault().changePassword(password(), password(NEW_PASSWORD)))
    }

    private fun writeRootLevelDatabase() =
        fixture.files.databaseFiles().onEach { it.writeBytes(ByteArray(8)) }

    @Test
    fun wipe_version1File_deletesItAndAllowsCreatingVersion2() = runBlocking<Unit> {
        writeVersion1File()
        val oldDatabase = writeRootLevelDatabase()
        val vault = fixture.vault()
        assertEquals(UnlockOutcome.LegacyFormat, vault.unlock(password()))

        assertEquals(WipeOutcome.SUCCESS, vault.wipe())

        assertFalse(fixture.files.vaultFile.exists())
        assertTrue(oldDatabase.none { it.exists() })
        assertEquals(DesktopVaultState.NoVault, vault.state.value)
        val fresh = DesktopVaultFixture(fixture.dataDirectory).vault()
        assertEquals(CreateOutcome.SUCCESS, fresh.create(password()))
        assertIs<DesktopVaultState.Unlocked>(fresh.state.value)
        assertTrue(fixture.files.vaultFile.exists())
        assertEquals(UnlockOutcome.Success, fresh.apply { lock() }.unlock(password()))
    }

    @Test
    fun create_oldRootLevelDatabaseLeftBehind_deletesIt() = runBlocking<Unit> {
        val oldDatabase = writeRootLevelDatabase()

        assertEquals(CreateOutcome.SUCCESS, fixture.vault().create(password()))

        assertTrue(oldDatabase.none { it.exists() })
    }

    private companion object {
        const val VERSION_1: Byte = 1
    }
}
