package cash.p.terminal.wallet

import io.horizontalsystems.core.DefaultDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountManagerVisibleAccountsTest {
    private val storage = mockk<IAccountsStorage>(relaxed = true)
    private val manager = AccountManager(
        storage = storage,
        getMoneroWalletFilesNameUseCase = mockk(relaxed = true),
        removeMoneroWalletFilesUseCase = mockk(relaxed = true),
        balanceHiddenManager = mockk(relaxed = true),
        deletionPreflight = mockk(relaxed = true),
        dispatcherProvider = DefaultDispatcherProvider(),
    )

    @Test
    fun visibleAccounts_beforeAnyLevelIsLoaded_isNull() {
        assertNull(manager.visibleAccounts)
    }

    @Test
    fun visibleAccounts_afterSetLevel_holdsTheLevelAndExactlyTheLoadedAccounts() {
        every { storage.allAccounts(1) } returns listOf(account("a"), account("b"))

        manager.setLevel(1)

        assertEquals(VisibleAccounts(1, setOf("a", "b")), manager.visibleAccounts)
    }

    @Test
    fun visibleAccounts_afterSetLevelOfAHiddenWallet_holdsOnlyItsOwnAccounts() {
        every { storage.allAccounts(0) } returns listOf(account("main"))
        every { storage.allAccounts(-1) } returns listOf(account("hidden"))
        manager.setLevel(0)

        manager.setLevel(-1)

        assertEquals(VisibleAccounts(-1, setOf("hidden")), manager.visibleAccounts)
    }

    @Test
    fun visibleAccounts_afterSaveAndImport_includesTheNewAccounts() {
        manager.setLevel(0)

        manager.save(account("saved"))
        manager.import(listOf(account("imported")))

        assertEquals(VisibleAccounts(0, setOf("saved", "imported")), manager.visibleAccounts)
    }

    @Test
    fun visibleAccounts_afterDelete_dropsTheDeletedAccount() = runTest {
        every { storage.allAccounts(0) } returns listOf(account("kept"), account("gone"))
        manager.setLevel(0)

        manager.delete("gone")

        assertEquals(VisibleAccounts(0, setOf("kept")), manager.visibleAccounts)
    }

    @Test
    fun visibleAccounts_afterClear_isEmptyAtTheSameLevel() {
        every { storage.allAccounts(0) } returns listOf(account("a"))
        manager.setLevel(0)

        manager.clear()

        assertEquals(VisibleAccounts(0, emptySet()), manager.visibleAccounts)
    }

    @Test
    fun visibleAccounts_afterUnload_isNull() {
        every { storage.allAccounts(0) } returns listOf(account("a"))
        manager.setLevel(0)

        manager.unload()

        assertNull(manager.visibleAccounts)
    }

    @Test
    fun visibleAccounts_keystoreCannotDecodeTheNewLevel_matchesTheEmptyCache() {
        every { storage.allAccounts(0) } returns listOf(account("a"))
        manager.setLevel(0)
        every { storage.allAccounts(1) } returns emptyList()
        every { storage.getActiveAccountId(1) } returns "unreadable"

        manager.setLevel(1)

        assertEquals(emptyList<Account>(), manager.accounts)
        assertEquals(VisibleAccounts(1, emptySet()), manager.visibleAccounts)
    }

    @Test
    fun updateName_levelChangesInsideTheStorageWrite_doesNotReinsertTheAccount() {
        loadLevels()
        manager.setLevel(1)
        every { storage.updateName("upper", any()) } answers { manager.setLevel(0) }

        manager.updateName("upper", "renamed")

        assertEquals(listOf("main", "gone"), manager.accounts.map { it.id })
        assertEquals(VisibleAccounts(0, setOf("main", "gone")), manager.visibleAccounts)
    }

    @Test
    fun updateName_sameLevelReloadedInsideTheStorageWrite_keepsTheReloadedAccount() {
        every { storage.allAccounts(0) } returns listOf(account("main"))
        manager.setLevel(0)
        val reloaded = account("main").copy(name = "reloaded")
        every { storage.updateName("main", any()) } answers {
            every { storage.allAccounts(0) } returns listOf(reloaded)
            manager.setLevel(0)
        }

        manager.updateName("main", "renamed")

        assertEquals(listOf("reloaded"), manager.accounts.map { it.name })
    }

    @Test
    fun markAsBackedUp_levelChangesInsideTheStorageWrite_doesNotReinsertTheAccount() {
        loadLevels()
        manager.setLevel(1)
        every { storage.updateBackupFlags("upper", any(), any()) } answers { manager.setLevel(0) }

        manager.markAsBackedUp("upper")

        assertEquals(listOf("main", "gone"), manager.accounts.map { it.id })
        assertEquals(VisibleAccounts(0, setOf("main", "gone")), manager.visibleAccounts)
    }

    @Test
    fun save_levelChangesInsideTheStorageWrite_doesNotAddTheAccountToTheNewLevel() {
        loadLevels()
        manager.setLevel(0)
        every { storage.save(account("late")) } answers { manager.setLevel(1) }

        manager.save(account("late"))

        assertEquals(listOf("upper"), manager.accounts.map { it.id })
        assertEquals(VisibleAccounts(1, setOf("upper")), manager.visibleAccounts)
    }

    @Test
    fun delete_levelChangesInsideTheStorageCall_leavesTheNewLevelWithItsOwnNumberAndIds() = runTest {
        loadLevels()
        manager.setLevel(0)
        every { storage.delete("main") } answers { manager.setLevel(1) }

        manager.delete("main")

        assertEquals(VisibleAccounts(1, setOf("upper")), manager.visibleAccounts)
    }

    @Test
    fun delete_finishesBetweenTheCacheReplacementAndTheLevelSwitch_keepsTheNewLevelNumber() {
        loadLevels()
        manager.setLevel(0)
        every { storage.getActiveAccountId(1) } answers {
            runBlocking { manager.delete("gone") }
            null
        }

        manager.setLevel(1)

        assertEquals(VisibleAccounts(1, setOf("upper")), manager.visibleAccounts)
    }

    @Test
    fun save_afterTheKeystoreEarlyReturn_publishesUnderTheRequestedLevel() {
        every { storage.allAccounts(0) } returns listOf(account("a"))
        manager.setLevel(0)
        every { storage.allAccounts(1) } returns emptyList()
        every { storage.getActiveAccountId(1) } returns "unreadable"
        manager.setLevel(1)

        manager.save(account("new"), updateActive = false)

        assertEquals(VisibleAccounts(1, setOf("new")), manager.visibleAccounts)
    }

    @Test
    fun accounts_afterLoadSaveAndRename_keepTheInsertionOrder() {
        every { storage.allAccounts(0) } returns listOf(account("b"), account("a"), account("c"))
        manager.setLevel(0)

        manager.save(account("d"), updateActive = false)
        manager.updateName("a", "renamed")

        assertEquals(listOf("b", "a", "c", "d"), manager.accounts.map { it.id })
    }

    private fun loadLevels() {
        every { storage.allAccounts(0) } returns listOf(account("main"), account("gone"))
        every { storage.allAccounts(1) } returns listOf(account("upper"))
    }

    private fun account(id: String) = Account(
        id = id, name = id, type = AccountType.EvmAddress("address-$id"),
        origin = AccountOrigin.Created, level = 0, isBackedUp = true, isFileBackedUp = true,
    )
}
