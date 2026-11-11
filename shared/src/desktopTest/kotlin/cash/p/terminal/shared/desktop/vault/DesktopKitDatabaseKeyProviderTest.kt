package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DesktopKitDatabaseKeyProviderTest {

    private val fixture = VaultLevelsFixture()
    private val evmKeys get() = DesktopEvmKitDatabaseKeyProvider(fixture.vault)

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun awaitKey_sameKitAndAccount_returnsTheSameKeyAfterReopeningTheVault() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        val key = evmKeys.awaitKey(ACCOUNT_ID)

        fixture.reloginOk()

        assertContentEquals(key, evmKeys.awaitKey(ACCOUNT_ID))
    }

    @Test
    fun awaitKey_otherKitOrAccount_returnsADifferentKey() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        fixture.addAccount(OTHER_ACCOUNT_ID)
        val key = evmKeys.awaitKey(ACCOUNT_ID)

        assertFalse(key.contentEquals(evmKeys.awaitKey(OTHER_ACCOUNT_ID)))
        assertFalse(key.contentEquals(DesktopBitcoinKitDatabaseKeyProvider(fixture.vault).awaitKey(ACCOUNT_ID)))
    }

    @Test
    fun awaitDatabaseKey_repeatedCalls_isNewOnlyTheFirstTime() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)

        assertTrue(evmKeys.awaitDatabaseKey(ACCOUNT_ID).isNew)
        assertFalse(evmKeys.awaitDatabaseKey(ACCOUNT_ID).isNew)
    }

    @Test
    fun remove_afterAKitClear_keepsTheKeyTheAccountsFilesNeed() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        val key = evmKeys.awaitKey(ACCOUNT_ID)

        evmKeys.remove(ACCOUNT_ID)

        assertContentEquals(key, evmKeys.awaitKey(ACCOUNT_ID))
    }

    @Test
    fun drop_zcashKey_makesTheNextKeyANewOne() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        val zcashKeys = DesktopZcashDbKeyProvider(fixture.vault)
        val key = zcashKeys.keyFor(ACCOUNT_ID)

        assertTrue(zcashKeys.drop(ACCOUNT_ID))

        val next = zcashKeys.keyFor(ACCOUNT_ID)
        assertTrue(next.newlyGenerated)
        assertFalse(key.bytes.contentEquals(next.bytes))
    }

    @Test
    fun awaitKey_sessionClosed_throwsStorageLocked() = runTest {
        fixture.create()
        fixture.addAccount(ACCOUNT_ID)
        fixture.vault.close()

        assertFailsWith<StorageLockedException> { evmKeys.awaitKey(ACCOUNT_ID) }
    }

    @Test
    fun awaitKey_accountOfNoOpenStore_throwsStorageLocked() = runTest {
        fixture.create()

        assertFailsWith<StorageLockedException> { evmKeys.awaitKey(ACCOUNT_ID) }
    }

    @Test
    fun awaitKey_beforeFirstUnlock_throwsStorageLocked() = runTest {
        assertFailsWith<StorageLockedException> { evmKeys.awaitKey(ACCOUNT_ID) }
    }

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val OTHER_ACCOUNT_ID = "other-account"
    }
}
