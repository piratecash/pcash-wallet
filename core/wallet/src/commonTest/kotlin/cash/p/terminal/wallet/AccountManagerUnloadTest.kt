package cash.p.terminal.wallet

import io.horizontalsystems.core.DefaultDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagerUnloadTest {
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
    fun unload_afterSaveOfUnbackedAccount_dropsEveryAccountReference() = runTest {
        manager.save(account("old"))
        val emitted = mutableListOf<List<Account>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.accountsFlow.collect { emitted += it }
        }

        manager.unload()

        assertNull(manager.newAccountBackupRequiredFlow.value)
        assertEquals(emptyList<Account>(), manager.accounts)
        assertNull(manager.activeAccount)
        assertSame(ActiveAccountState.NotLoaded, manager.activeAccountStateFlow.value)
        assertEquals(listOf(emptyList<Account>()), emitted)
    }

    @Test
    fun setLevel_afterUnload_reloadsAccountsFromStorage() {
        manager.save(account("old"))
        manager.unload()
        val newAccount = account("new")
        every { storage.allAccounts(0) } returns listOf(newAccount)
        every { storage.getActiveAccountId(0) } returns newAccount.id

        manager.setLevel(0)

        assertEquals(listOf(newAccount), manager.accounts)
        assertEquals(newAccount, manager.activeAccount)
    }

    private fun account(id: String) = Account(
        id = id, name = id, type = AccountType.EvmAddress("address-$id"),
        origin = AccountOrigin.Created, level = 0, isBackedUp = false, isFileBackedUp = false,
    )
}
