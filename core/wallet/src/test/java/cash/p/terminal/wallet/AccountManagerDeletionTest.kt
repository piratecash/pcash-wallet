package cash.p.terminal.wallet

import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.mockk.clearMocks
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class AccountManagerDeletionTest {
    private val storage = mockk<IAccountsStorage>(relaxed = true)
    private val moneroFiles = mockk<IGetMoneroWalletFilesNameUseCase>(relaxed = true)
    private val removeFiles = mockk<RemoveMoneroWalletFilesUseCase>(relaxed = true)
    private val balanceHidden = mockk<IBalanceHiddenManager>(relaxed = true)
    private val preflight = mockk<AccountDeletionPreflight>(relaxed = true)
    private val manager = AccountManager(storage, moneroFiles, removeFiles, balanceHidden, preflight)
    private val account = Account(
        id = "account", name = "Account", type = AccountType.EvmAddress("address"),
        origin = AccountOrigin.Created, level = 0, isBackedUp = false, isFileBackedUp = false,
    )

    @Test
    fun delete_blocked_preservesFilesAccountSelectionAndBackupState() = runTest {
        manager.save(account)
        clearMocks(storage, balanceHidden, answers = false)
        coEvery { preflight.ensureCanDelete(listOf(account.id)) } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { manager.delete(account.id) }

        verify { listOf(storage, moneroFiles, removeFiles, balanceHidden) wasNot Called }
        assertEquals(account, manager.activeAccount)
        assertEquals(listOf(account), manager.accounts)
        assertEquals(account, manager.newAccountBackupRequiredFlow.value)
    }

    @Test
    fun delete_tombstoneFailure_doesNotCleanOrChangeSelection() = runTest {
        manager.save(account)
        clearMocks(storage, balanceHidden, answers = false)
        every { storage.delete(account.id) } throws IllegalStateException("database write failed")

        assertFailsWith<AccountDeletionBlockedException> { manager.delete(account.id) }

        coVerify(exactly = 0) { preflight.cleanupDeleted(any()) }
        verify { listOf(moneroFiles, removeFiles, balanceHidden) wasNot Called }
        assertEquals(account, manager.activeAccount)
    }

    @Test
    fun delete_cleanupFailure_keepsDurableTombstoneAndDoesNotReportSuccess() = runTest {
        manager.save(account)
        coEvery { preflight.cleanupDeleted(listOf(account.id)) } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { manager.delete(account.id) }

        verify(exactly = 1) { storage.delete(account.id) }
        verify { listOf(moneroFiles, removeFiles) wasNot Called }
        assertEquals(account, manager.activeAccount)
    }

    @Test
    fun delete_unrelatedAccount_preflightsBeforeMoneroAndTombstone() = runTest {
        every { storage.loadAccount(account.id) } returns account
        coEvery { moneroFiles(account) } returns null

        manager.delete(account.id)

        coVerifyOrder {
            preflight.ensureCanDelete(listOf(account.id))
            storage.delete(account.id)
            preflight.cleanupDeleted(listOf(account.id))
            moneroFiles(account)
        }
    }
}
