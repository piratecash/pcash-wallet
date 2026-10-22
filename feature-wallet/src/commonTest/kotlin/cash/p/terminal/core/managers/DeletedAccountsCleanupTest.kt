package cash.p.terminal.core.managers

import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAccountsStorage
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class DeletedAccountsCleanupTest {
    private val manager = mockk<IAccountManager>(relaxed = true)
    private val storage = mockk<IAccountsStorage>(relaxed = true)
    private val restore = mockk<RestoreSettingsManager>(relaxed = true)
    private val cleaner = mockk<IAccountCleaner>(relaxed = true)
    private val preflight = mockk<AccountDeletionPreflight>(relaxed = true)
    private val cleanup = DeletedAccountsCleanup(manager, storage, restore, cleaner, preflight)
    private val ids = listOf("deleted-account")

    // Startup runs this without a CoroutineExceptionHandler, so a still-blocked reset must not
    // escape; the marker is retained and unrelated deleted accounts are still cleaned.
    @Test
    fun invoke_pendingResetCleanupFails_doesNotPropagateAndStillCleansTombstones() = runTest {
        every { manager.getDeletedAccountIds() } returns ids
        coEvery { preflight.resumePendingReset() } throws AccountDeletionBlockedException()

        cleanup()

        coVerifyOrder {
            preflight.resumePendingReset()
            restore.backfillTrezorMoneroRestoreHeights(any())
            preflight.ensureCanDelete(ids)
            cleaner.clearAccounts(ids)
            manager.clearDeleted(ids)
        }
    }

    @Test
    fun invoke_pendingResetCancelled_propagatesCancellation() = runTest {
        coEvery { preflight.resumePendingReset() } throws CancellationException()

        assertFailsWith<CancellationException> { cleanup() }

        verify { listOf(manager, storage, restore, cleaner) wasNot Called }
    }

    @Test
    fun invoke_blockedTombstone_retainsDataAndRetriesOnNextInvocation() = runTest {
        every { manager.getDeletedAccountIds() } returns ids
        coEvery { preflight.ensureCanDelete(ids) } throws AccountDeletionBlockedException()

        repeat(2) { cleanup() }

        verify(exactly = 2) { manager.getDeletedAccountIds() }
        verify(exactly = 0) { manager.clearDeleted(any()) }
        coVerify(exactly = 0) { cleaner.clearAccounts(any()) }
        verify(exactly = 2) { restore.backfillTrezorMoneroRestoreHeights(any()) }
    }

    @Test
    fun invoke_allowedTombstone_clearsMarkerOnlyAfterArtifacts() = runTest {
        every { manager.getDeletedAccountIds() } returns ids

        cleanup()

        coVerifyOrder {
            restore.backfillTrezorMoneroRestoreHeights(any())
            preflight.ensureCanDelete(ids)
            cleaner.clearAccounts(ids)
            manager.clearDeleted(ids)
        }
    }

    @Test
    fun invoke_noTombstones_preservesActiveAccountRestoreHeightBackfill() = runTest {
        val activeAccounts = listOf(mockk<Account>())
        every { manager.accounts } returns activeAccounts

        cleanup()

        verify { restore.backfillTrezorMoneroRestoreHeights(activeAccounts) }
    }

    @Test
    fun invoke_lastBarrierBlocks_retainsTombstone() = runTest {
        every { manager.getDeletedAccountIds() } returns ids
        coEvery { cleaner.clearAccounts(ids) } throws AccountDeletionBlockedException()

        cleanup()

        verify(exactly = 0) { manager.clearDeleted(any()) }
    }

    @Test
    fun invoke_mixedTombstones_cleansUnrelatedAccountAndRetainsBlockedBeam() = runTest {
        val blocked = "beam-account"
        val allowed = "other-account"
        every { manager.getDeletedAccountIds() } returns listOf(blocked, allowed)
        coEvery { preflight.ensureCanDelete(listOf(blocked)) } throws AccountDeletionBlockedException()

        cleanup()

        coVerifyOrder {
            restore.backfillTrezorMoneroRestoreHeights(any())
            preflight.ensureCanDelete(listOf(blocked))
            preflight.ensureCanDelete(listOf(allowed))
            cleaner.clearAccounts(listOf(allowed))
            manager.clearDeleted(listOf(allowed))
        }
        verify(exactly = 0) { manager.clearDeleted(listOf(blocked)) }
    }
}
