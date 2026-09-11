package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Wallet
import cash.p.zcash.MempoolEvent
import cash.p.zcash.PoolBalance
import cash.p.zcash.Transaction
import cash.p.zcash.ZcashWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT_ID = "account"
private const val DRAIN_TIMEOUT_MS = 30_000L

private fun ZcashSessionManager.privateMutex(name: String): Mutex =
    ZcashSessionManager::class.java.getDeclaredField(name)
        .apply { isAccessible = true }
        .get(this) as Mutex

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashSessionManagerTest {

    private val zcashWallet = mockk<ZcashWallet>(relaxed = true) {
        every { mempool() } returns flow<MempoolEvent> { awaitCancellation() }
        coEvery { balance(any(), any()) } returns PoolBalance(emptyMap())
        coEvery { transactions(any()) } returns emptyList<Transaction>()
        coEvery { latestHeight() } returns 0
    }

    private val account = mockk<Account> { every { id } returns ACCOUNT_ID }
    private val wallet = mockk<Wallet> { every { this@mockk.account } returns this@ZcashSessionManagerTest.account }

    private val walletOpener = mockk<ZcashWalletOpener> {
        coEvery { open(any()) } returns OpenedZcashWallet(zcashWallet, 0)
    }

    private val scheduler = mockk<ZcashSyncScheduler>(relaxed = true)

    private val offlineModeManager = mockk<OfflineModeManager> {
        every { isNetworkPaused(any<OfflineKey>()) } returns false
    }

    private fun TestScope.manager() = ZcashSessionManager(
        walletOpener = walletOpener,
        scheduler = scheduler,
        offlineModeManager = offlineModeManager,
        dispatcherProvider = TestDispatcherProvider(
            dispatcher = StandardTestDispatcher(testScheduler),
            applicationScope = backgroundScope,
        ),
    )

    @Test
    fun acquire_twiceForOneAccount_opensTheWalletOnce() = runTest {
        val manager = manager()

        val first = manager.acquire(wallet)
        val second = manager.acquire(wallet)

        assertSame(first, second)
        coVerify(exactly = 1) { walletOpener.open(wallet) }
    }

    @Test
    fun release_withRemainingReference_keepsTheWalletOpen() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        manager.acquire(wallet)

        manager.release(session)
        advanceUntilIdle()

        coVerify(exactly = 0) { zcashWallet.close() }
    }

    @Test
    fun release_lastReference_closesTheWallet() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)

        manager.release(session)

        coVerify(exactly = 1) { zcashWallet.close() }
    }

    @Test
    fun acquire_networkPaused_doesNotSubscribeToMempool() = runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(ACCOUNT_ID, BlockchainType.Zcash)) } returns true
        val manager = manager()

        manager.acquire(wallet)
        advanceUntilIdle()

        verify(exactly = 0) { zcashWallet.mempool() }
    }

    @Test
    fun release_drainTimesOut_reusesTheSameSession() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()

        manager.release(session)
        runCurrent()

        coVerify(exactly = 0) { zcashWallet.close() }
        coVerify(exactly = 1) { scheduler.enqueue(session) }
        verify(exactly = 1) { zcashWallet.mempool() }
        assertSame(session, manager.acquire(wallet))
        runCurrent()
        coVerify(exactly = 2) { scheduler.enqueue(session) }
        verify(exactly = 2) { zcashWallet.mempool() }
        coVerify(exactly = 1) { walletOpener.open(wallet) }
    }

    @Test
    fun closeForErase_lastOwnerReleasesDuringTimeout_keepsTheSessionDormant() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        val busy = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                busy.await()
            }
        }
        started.await()
        val erasing = async { manager.closeForErase(ACCOUNT_ID) }
        runCurrent()
        val releasing = async { manager.release(session) }
        runCurrent()

        advanceTimeBy(DRAIN_TIMEOUT_MS + 1)
        runCurrent()

        assertFalse(erasing.await())
        coVerify(exactly = 1) { scheduler.enqueue(session) }
        verify(exactly = 1) { zcashWallet.mempool() }

        busy.complete(Unit)
        advanceUntilIdle()
        releasing.await()

        coVerify(exactly = 1) { zcashWallet.close() }
    }

    @Test
    fun acquire_enqueueInProgress_closeForEraseWaitsForAcquisition() = runTest {
        val enqueueStarted = CompletableDeferred<Unit>()
        val allowEnqueue = CompletableDeferred<Unit>()
        coEvery { scheduler.enqueue(any()) } coAnswers {
            enqueueStarted.complete(Unit)
            allowEnqueue.await()
        }
        val manager = manager()
        val acquiring = async { manager.acquire(wallet) }
        enqueueStarted.await()

        val erasing = async { manager.closeForErase(ACCOUNT_ID) }
        runCurrent()

        assertFalse(acquiring.isCompleted)
        assertFalse(erasing.isCompleted)
        coVerify(exactly = 0) { zcashWallet.close() }

        allowEnqueue.complete(Unit)
        acquiring.await()
        assertTrue(erasing.await())
        coVerify(exactly = 1) { zcashWallet.close() }
    }

    @Test
    fun release_cancelledDuringDrain_restoresTheSameSession() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        val releasing = launch { manager.release(session) }
        runCurrent()

        releasing.cancel()
        advanceTimeBy(DRAIN_TIMEOUT_MS + 1)
        runCurrent()

        assertSame(session, manager.acquire(wallet))
        coVerify(exactly = 1) { walletOpener.open(wallet) }
    }

    @Test
    fun release_cancelledWhileWaitingForCloseGate_closesTheWallet() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        val closeGate = manager.privateMutex("closeGate")
        closeGate.lock()
        val releasing = launch { manager.release(session) }
        runCurrent()

        releasing.cancel()
        closeGate.unlock()
        advanceUntilIdle()

        coVerify(exactly = 1) { zcashWallet.close() }
    }

    @Test
    fun closeForErase_sessionStillHeldByAdapters_closesTheWallet() = runTest {
        val manager = manager()
        manager.acquire(wallet)
        manager.acquire(wallet)

        val closed = manager.closeForErase(ACCOUNT_ID)

        assertTrue(closed)
        coVerify(exactly = 1) { zcashWallet.close() }
    }

    @Test
    fun closeForErase_unknownAccount_reportsClosed() = runTest {
        assertTrue(manager().closeForErase(ACCOUNT_ID))
    }

    @Test
    fun closeForErase_duringActiveSync_cancelsItAndCloses() = runTest {
        val syncing = CompletableDeferred<Unit>()
        every { zcashWallet.sync(any()) } returns flow { syncing.await() }
        coEvery { zcashWallet.cancelSync() } answers { syncing.complete(Unit) }
        val manager = manager()
        val session = manager.acquire(wallet)
        backgroundScope.launch { session.sync() }
        advanceUntilIdle()

        val closed = manager.closeForErase(ACCOUNT_ID)

        assertTrue(closed)
        coVerify(exactly = 1) { zcashWallet.cancelSync() }
        coVerify(exactly = 1) { zcashWallet.close() }
    }

    @Test
    fun closeForErase_drainTimesOut_returnsTheSessionToService() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()

        val closed = manager.closeForErase(ACCOUNT_ID)

        assertFalse(closed)
        coVerify(exactly = 0) { zcashWallet.close() }
        assertSame(session, manager.acquire(wallet))
        coVerify(exactly = 1) { walletOpener.open(wallet) }
    }

    @Test
    fun closeForErase_whileAReleaseIsDraining_doesNotReportTheSessionClosed() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        backgroundScope.launch { manager.release(session) }
        runCurrent()

        val closed = manager.closeForErase(ACCOUNT_ID)

        assertFalse(closed)
        coVerify(exactly = 0) { zcashWallet.close() }
    }

    @Test
    fun closeForErase_drainTimesOut_keepsTheReferencesAdaptersStillHold() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        val busy = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                busy.await()
            }
        }
        started.await()
        assertFalse(manager.closeForErase(ACCOUNT_ID))
        busy.complete(Unit)
        advanceUntilIdle()

        manager.release(session)
        advanceUntilIdle()

        coVerify(exactly = 0) { zcashWallet.close() }
        assertSame(session, manager.acquire(wallet))
    }

    @Test
    fun release_duringADrainThatTimesOut_stillDropsItsReference() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        val busy = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                busy.await()
            }
        }
        started.await()
        backgroundScope.launch { manager.closeForErase(ACCOUNT_ID) }
        runCurrent()
        backgroundScope.launch { manager.release(session) }
        runCurrent()
        advanceUntilIdle()
        busy.complete(Unit)
        advanceUntilIdle()

        manager.release(session)
        advanceUntilIdle()

        coVerify(exactly = 1) { zcashWallet.close() }
    }

    @Test
    fun release_ofAnErasedSession_keepsItsReplacementOpen() = runTest {
        val replacement = mockk<ZcashWallet>(relaxed = true) {
            every { mempool() } returns flow<MempoolEvent> { awaitCancellation() }
            coEvery { balance(any(), any()) } returns PoolBalance(emptyMap())
            coEvery { transactions(any()) } returns emptyList<Transaction>()
            coEvery { latestHeight() } returns 0
        }
        coEvery { walletOpener.open(any()) } returnsMany listOf(
            OpenedZcashWallet(zcashWallet, 0),
            OpenedZcashWallet(replacement, 0),
        )
        val manager = manager()
        val erased = manager.acquire(wallet)
        manager.closeForErase(ACCOUNT_ID)
        manager.acquire(wallet)

        manager.release(erased)

        coVerify(exactly = 0) { replacement.close() }
    }

    @Test
    fun acquire_whileACloseIsDraining_reusesTheSessionInsteadOfOpeningASecondWallet() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        val busy = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation { started.complete(Unit); busy.await() }
        }
        started.await()
        val erase = async { manager.closeForErase(ACCOUNT_ID) }
        runCurrent()

        val acquired = async { manager.acquire(wallet) }
        runCurrent()
        assertFalse(acquired.isCompleted)

        advanceUntilIdle()
        busy.complete(Unit)
        advanceUntilIdle()

        assertFalse(erase.await())
        assertSame(session, acquired.await())
        coVerify(exactly = 1) { walletOpener.open(wallet) }
    }

    @Test
    fun release_whileACloseTakesTheEntry_stillDropsItsReference() = runTest {
        val manager = manager()
        val session = manager.acquire(wallet)
        manager.acquire(wallet)
        val started = CompletableDeferred<Unit>()
        val busy = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation { started.complete(Unit); busy.await() }
        }
        started.await()

        // Queueing both on the manager's own lock puts the release exactly inside the handoff of
        // the entry from the registry to the close — the only window where it can be lost.
        val mutex = manager.privateMutex("mutex")
        mutex.lock()
        val erase = async { manager.closeForErase(ACCOUNT_ID) }
        runCurrent()
        val released = async { manager.release(session) }
        runCurrent()
        mutex.unlock()
        advanceUntilIdle()
        released.await()
        assertFalse(erase.await())

        busy.complete(Unit)
        manager.release(session)
        advanceUntilIdle()

        coVerify(exactly = 1) { zcashWallet.close() }
    }
}
