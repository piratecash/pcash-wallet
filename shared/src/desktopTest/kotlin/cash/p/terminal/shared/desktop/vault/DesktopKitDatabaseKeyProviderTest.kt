package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DesktopKitDatabaseKeyProviderTest {

    private val fixture = DesktopVaultFixture()
    private val vault = fixture.vault()
    private val evmKeys = DesktopEvmKitDatabaseKeyProvider(vault)

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun awaitKey_sameKitAndAccount_returnsTheSameKeyAfterReopeningTheVault() = runTest {
        vault.create(password())
        val key = evmKeys.awaitKey(ACCOUNT_ID)

        vault.close()
        assertEquals(UnlockOutcome.Success, vault.unlock(password()))

        assertContentEquals(key, evmKeys.awaitKey(ACCOUNT_ID))
    }

    @Test
    fun awaitKey_otherKitOrAccount_returnsADifferentKey() = runTest {
        vault.create(password())
        val key = evmKeys.awaitKey(ACCOUNT_ID)

        assertFalse(key.contentEquals(evmKeys.awaitKey("other-account")))
        assertFalse(key.contentEquals(DesktopBitcoinKitDatabaseKeyProvider(vault).awaitKey(ACCOUNT_ID)))
    }

    @Test
    fun awaitDatabaseKey_existingVault_isNeverNew() = runTest {
        vault.create(password())

        assertFalse(evmKeys.awaitDatabaseKey(ACCOUNT_ID).isNew)
    }

    @Test
    fun awaitKey_sessionClosed_throwsStorageLocked() = runTest {
        vault.create(password())
        vault.close()

        assertFailsWith<StorageLockedException> { evmKeys.awaitKey(ACCOUNT_ID) }
    }

    @Test
    fun awaitKey_beforeFirstUnlock_throwsStorageLocked() = runTest {
        assertFailsWith<StorageLockedException> { evmKeys.awaitKey(ACCOUNT_ID) }
    }

    private companion object {
        const val ACCOUNT_ID = "account-1"
    }
}
