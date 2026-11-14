package cash.p.terminal.wallet

import io.horizontalsystems.core.DispatcherProvider
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import io.mockk.clearMocks
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class AccountManagerDeletionTest {
    private val storage = mockk<IAccountsStorage>(relaxed = true)
    private val moneroFiles = mockk<IGetMoneroWalletFilesNameUseCase>(relaxed = true)
    private val removeFiles = mockk<IRemoveMoneroWalletFilesUseCase>(relaxed = true)
    private val balanceHidden = mockk<IBalanceHiddenManager>(relaxed = true)
    private val preflight = mockk<AccountDeletionPreflight>(relaxed = true)
    private val account = Account(
        id = "account", name = "Account", type = AccountType.EvmAddress("address"),
        origin = AccountOrigin.Created, level = 0, isBackedUp = false, isFileBackedUp = false,
    )
    private val other = account.copy(id = "other", name = "Other")

    // Background cleanup runs on backgroundScope: only runCurrent() executes it, advanceUntilIdle() skips it.
    private fun TestScope.manager(): AccountManager {
        val dispatchers = mockk<DispatcherProvider> {
            every { io } returns UnconfinedTestDispatcher(testScheduler)
            every { applicationScope } returns backgroundScope
        }
        return AccountManager(storage, moneroFiles, removeFiles, balanceHidden, preflight, dispatchers)
    }

    @Test
    fun delete_blocked_preservesFilesAccountSelectionAndBackupState() = runTest {
        val manager = manager()
        manager.save(account)
        clearMocks(storage, balanceHidden, answers = false)
        coEvery { preflight.ensureCanDelete(listOf(account.id)) } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { manager.delete(account.id) }
        runCurrent()

        verify { listOf(storage, moneroFiles, removeFiles, balanceHidden) wasNot Called }
        verify(exactly = 0) { preflight.fenceDeleted(any()) }
        coVerify(exactly = 0) { preflight.cleanupDeleted(any()) }
        assertEquals(account, manager.activeAccount)
        assertEquals(listOf(account), manager.accounts)
        assertEquals(account, manager.newAccountBackupRequiredFlow.value)
    }

    @Test
    fun delete_tombstoneFailure_doesNotCleanOrChangeSelection() = runTest {
        val manager = manager()
        manager.save(account)
        clearMocks(storage, balanceHidden, answers = false)
        every { storage.delete(account.id) } throws IllegalStateException("database write failed")

        assertFailsWith<AccountDeletionBlockedException> { manager.delete(account.id) }
        runCurrent()

        verify(exactly = 0) { preflight.fenceDeleted(any()) }
        coVerify(exactly = 0) { preflight.cleanupDeleted(any()) }
        verify { listOf(moneroFiles, removeFiles, balanceHidden) wasNot Called }
        assertEquals(account, manager.activeAccount)
        assertEquals(listOf(account), manager.accounts)
    }

    @Test
    fun delete_cleanupSuspended_updatesSessionBeforeCleanupFinishes() = runTest {
        val manager = manager()
        manager.save(other)
        manager.save(account)
        every { storage.loadAccount(account.id) } returns account
        coEvery { moneroFiles(account) } returns "wallet"
        val cleanupGate = CompletableDeferred<Unit>()
        coEvery { preflight.cleanupDeleted(listOf(account.id)) } coAnswers { cleanupGate.await() }

        manager.delete(account.id)

        assertEquals(listOf(other), manager.accounts)
        assertEquals(other, manager.activeAccount)
        coVerify(exactly = 0) { removeFiles(any<String>()) }

        cleanupGate.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { removeFiles("wallet") }
    }

    @Test
    fun delete_cleanupFailure_keepsTombstoneAndRemovesAccountFromSession() = runTest {
        val manager = manager()
        manager.save(account)
        every { storage.loadAccount(account.id) } returns account
        coEvery { preflight.cleanupDeleted(listOf(account.id)) } throws AccountDeletionBlockedException()

        manager.delete(account.id)
        runCurrent()

        coVerify(exactly = 1) { preflight.cleanupDeleted(listOf(account.id)) }
        verify(exactly = 1) { storage.delete(account.id) }
        verify(exactly = 0) { storage.clearDeleted(any()) }
        verify { listOf(moneroFiles, removeFiles) wasNot Called }
        assertEquals(emptyList<Account>(), manager.accounts)
        assertEquals(null, manager.activeAccount)
    }

    @Test
    fun delete_unrelatedAccount_preflightsBeforeMoneroAndTombstone() = runTest {
        val manager = manager()
        every { storage.loadAccount(account.id) } returns account
        coEvery { moneroFiles(account) } returns null

        manager.delete(account.id)
        runCurrent()

        coVerifyOrder {
            preflight.ensureCanDelete(listOf(account.id))
            storage.delete(account.id)
            preflight.fenceDeleted(listOf(account.id))
            preflight.cleanupDeleted(listOf(account.id))
            moneroFiles(account)
        }
    }

    @Test
    fun delete_activeAccount_fencesBeforeSwitchingActiveAccount() = runTest {
        val manager = manager()
        manager.save(other)
        manager.save(account)
        var activeAtFence: Account? = null
        every { preflight.fenceDeleted(listOf(account.id)) } answers { activeAtFence = manager.activeAccount }

        manager.delete(account.id)

        assertEquals(account, activeAtFence)
        assertEquals(other, manager.activeAccount)
    }
}
