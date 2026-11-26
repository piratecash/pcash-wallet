package cash.p.terminal.wallet

import io.horizontalsystems.core.DefaultDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountManagerSetLevelTest {
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
    fun setLevel_firstLoad_announcesActiveAccountBeforeTheFullListIsLoaded() {
        val active = account("active")
        every { storage.getActiveAccountId(0) } returns "active"
        every { storage.activeAccount(0) } returns active
        var stateDuringFullLoad: ActiveAccountState? = null
        var loadedDuringFullLoad: Boolean? = null
        every { storage.allAccounts(0) } answers {
            stateDuringFullLoad = manager.activeAccountStateFlow.value
            loadedDuringFullLoad = manager.accountsLoadedFlow.value
            listOf(active, account("other"))
        }

        manager.setLevel(0)

        assertEquals(active, (stateDuringFullLoad as ActiveAccountState.ActiveAccount).account)
        assertEquals(false, loadedDuringFullLoad)
        assertTrue(manager.accountsLoadedFlow.value)
        assertEquals(setOf("active", "other"), manager.accounts.map { it.id }.toSet())
    }

    @Test
    fun setLevel_hiddenWalletLevel_loadsOnlyTheFullList() {
        every { storage.allAccounts(-1) } returns listOf(account("hidden"))

        manager.setLevel(-1)

        verify(exactly = 0) { storage.activeAccount(any()) }
        assertEquals("hidden", manager.activeAccount?.id)
    }

    @Test
    fun setLevel_activeAccountAlreadyLoaded_doesNotAnnounceAnotherAhead() {
        every { storage.getActiveAccountId(any()) } returns "a"
        every { storage.allAccounts(0) } returns listOf(account("a"))
        manager.setLevel(0)

        manager.setLevel(0)

        verify(exactly = 1) { storage.activeAccount(any()) }
    }

    @Test
    fun setLevel_accountAnnouncedAheadMissingFromFullList_switchesToALoadedAccount() {
        every { storage.getActiveAccountId(0) } returns "gone"
        every { storage.activeAccount(0) } returns account("gone")
        every { storage.allAccounts(0) } returns listOf(account("kept"))

        manager.setLevel(0)

        assertEquals("kept", manager.activeAccount?.id)
        val state = manager.activeAccountStateFlow.value as ActiveAccountState.ActiveAccount
        assertEquals("kept", state.account?.id)
    }

    @Test
    fun unload_afterLoad_marksAccountsNotLoaded() {
        every { storage.allAccounts(0) } returns listOf(account("a"))
        manager.setLevel(0)

        manager.unload()

        assertFalse(manager.accountsLoadedFlow.value)
    }

    private fun account(id: String) = Account(
        id = id, name = id, type = AccountType.EvmAddress("address-$id"),
        origin = AccountOrigin.Created, level = 0, isBackedUp = true, isFileBackedUp = true,
    )
}
