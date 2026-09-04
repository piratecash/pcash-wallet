package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.AdapterState
import cash.p.zcash.Balance
import cash.p.zcash.Pool
import cash.p.zcash.PoolBalance
import cash.p.zcash.SyncState
import cash.p.zcash.Transaction
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Session lifecycle of [ZcashAdapter] and the mapping of the SDK's [SyncState] onto [AdapterState].
 *
 * The adapter owns exactly two things here: when a session is acquired and released, and how the
 * sync state is presented. Opening, closing and restarting the wallet itself belong to
 * [cash.p.terminal.core.adapters.zcash.session.ZcashSessionManager] and
 * [cash.p.terminal.core.adapters.zcash.session.ZcashSyncScheduler].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterLifecycleTest : ZcashAdapterTestFixture() {

    // --- session lifecycle ---

    @Test
    fun stop_thenEnterForeground_doesNotAcquireANewSession() = runTest(dispatcher) {
        startAdapter()

        adapter.stop()
        advanceUntilIdle()
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        coVerify(exactly = 1) { sessionManager.acquire(wallet) }
    }

    @Test
    fun stop_releaseInProgressForegroundArrives_doesNotReacquire() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        coEvery { sessionManager.release(session) } coAnswers { release.await() }
        startAdapter()

        adapter.stop()
        runCurrent()
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        runCurrent()

        coVerify(exactly = 1) { sessionManager.acquire(wallet) }
        release.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun enterBackground_thenEnterForeground_reacquiresTheSession() = runTest(dispatcher) {
        startAdapter()

        enterBackground()
        coVerify(exactly = 1) { sessionManager.release(session) }

        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        coVerify(exactly = 2) { sessionManager.acquire(wallet) }
    }

    @Test
    fun enterBackground_activePollingSession_keepsTheSession() = runTest(dispatcher) {
        adapter = createAdapter()
        adapter.startForPolling()
        advanceUntilIdle()

        enterBackground()

        coVerify(exactly = 0) { sessionManager.release(any()) }
    }

    @Test
    fun enterBackground_realtimeKeepAlive_keepsTheSession() = runTest(dispatcher) {
        every { backgroundKeepAliveManager.isKeepAlive(BlockchainType.Zcash) } returns true
        startAdapter()

        enterBackground()

        coVerify(exactly = 0) { sessionManager.release(any()) }
    }

    // --- sync state mapping ---

    @Test
    fun syncState_syncing_reportsProgressAgainstTheFirstScannedHeight() = runTest(dispatcher) {
        startAdapter()

        emitSyncing(current = 2_500_000, target = 3_500_000)
        assertEquals(AdapterState.Syncing(progress = 0.0, blocksRemained = 1_000_000L), adapter.balanceState)

        emitSyncing(current = 3_000_000, target = 3_500_000)
        assertEquals(AdapterState.Syncing(progress = 50.0, blocksRemained = 500_000L), adapter.balanceState)
    }

    @Test
    fun syncState_syncedThenSyncing_anchorsToTheNewPass() = runTest(dispatcher) {
        startAdapter()
        emitSyncing(current = 2_500_000, target = 3_500_000)
        emitSyncing(current = 3_000_000, target = 3_500_000)

        emitSessionSyncState(SyncState.Synced)
        advanceUntilIdle()
        assertEquals(AdapterState.Synced, adapter.balanceState)

        emitSyncing(current = 3_500_000, target = 3_600_000)
        assertEquals(AdapterState.Syncing(progress = 0.0, blocksRemained = 100_000L), adapter.balanceState)
    }

    @Test
    fun syncState_syncedThenCaughtUpPoll_keepsSynced() = runTest(dispatcher) {
        startAdapter()
        emitSessionSyncState(SyncState.Synced)
        advanceUntilIdle()

        emitSessionSyncState(SyncState.Connecting)
        advanceUntilIdle()
        assertEquals(AdapterState.Synced, adapter.balanceState)

        emitSyncing(current = 3_500_000, target = 3_500_000)
        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    @Test
    fun syncState_failed_reportsNotSynced() = runTest(dispatcher) {
        startAdapter()
        val error = IOException("sync failed")

        emitSessionSyncState(SyncState.Failed(error))
        advanceUntilIdle()

        assertEquals(AdapterState.NotSynced(error), adapter.balanceState)
    }

    @Test
    fun syncState_stopped_reportsConnecting() = runTest(dispatcher) {
        startAdapter()

        emitSessionSyncState(SyncState.Stopped)
        advanceUntilIdle()

        assertEquals(AdapterState.Connecting, adapter.balanceState)
    }

    @Test
    fun sessionState_synced_publishesFreshDataBeforeTheSyncedNotification() = runTest(dispatcher) {
        startAdapter()
        var observedPending = 0L.convertZatoshiToZec()
        var observedHeight = 0
        var observedTransactionHashes = emptyList<String>()
        val subscription = adapter.transactionsStateUpdatedFlowable.subscribe {
            if (adapter.transactionsState == AdapterState.Synced) {
                observedPending = adapter.balanceData.pending
                observedHeight = adapter.lastBlockInfo?.height ?: 0
                observedTransactionHashes = runBlocking {
                    adapter.getTransactions(
                        from = null,
                        token = null,
                        limit = 10,
                        transactionType = FilterTransactionType.All,
                        address = null,
                    ).map { it.transactionHash }
                }
            }
        }

        emitSessionState(
            syncState = SyncState.Synced,
            balance = PoolBalance(mapOf(Pool.SAPLING to Balance(valuePending = RECEIVED))),
            transactions = listOf(transaction()),
            latestHeight = TARGET_HEIGHT,
        )
        advanceUntilIdle()

        assertEquals(RECEIVED.convertZatoshiToZec(), observedPending)
        assertEquals(TARGET_HEIGHT, observedHeight)
        assertEquals(listOf(TXID), observedTransactionHashes)
        subscription.dispose()
    }

    @Test
    fun sessionState_progressOnlyUpdate_doesNotNotifyUnchangedData() = runTest(dispatcher) {
        startAdapter()
        emitSyncing(current = 100, target = TARGET_HEIGHT)
        var balanceUpdates = 0
        var transactionUpdates = 0
        backgroundScope.launch { adapter.balanceUpdatedFlow.collect { balanceUpdates++ } }
        backgroundScope.launch {
            adapter.getTransactionRecordsFlow(null, FilterTransactionType.All, null)
                .collect { transactionUpdates++ }
        }
        val heightObserver = adapter.lastBlockUpdatedFlowable.test()
        runCurrent()

        emitSyncing(current = 150, target = TARGET_HEIGHT)

        assertEquals(0, balanceUpdates)
        assertEquals(0, transactionUpdates)
        heightObserver.assertNoValues()
    }

    private fun TestScope.startAdapter() {
        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()
    }

    private fun TestScope.enterBackground() {
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()
    }

    private fun TestScope.emitSyncing(current: Int, target: Int) {
        emitSessionSyncState(SyncState.Syncing(current = current, target = target))
        advanceUntilIdle()
    }

    private fun transaction() = Transaction(
        id = 1,
        txid = TXID,
        height = TARGET_HEIGHT,
        time = 1_700_000_000L,
        value = RECEIVED,
        memo = null,
        fee = 0,
        totalReceived = RECEIVED,
        isChange = false,
        recipient = null,
    )

    private companion object {
        const val TARGET_HEIGHT = 2_500_000
        const val RECEIVED = 120_000L
        const val TXID = "5f2c"
    }
}
