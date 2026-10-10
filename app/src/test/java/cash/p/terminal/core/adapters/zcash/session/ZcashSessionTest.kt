package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.CapturedAppLog
import cash.p.terminal.core.adapters.zcash.ZcashSessionDiagnostics
import cash.p.terminal.core.adapters.zcash.zcashAppLogger
import cash.p.terminal.core.adapters.zcash.zcashRestartDelayFor
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.NotBroadcastException
import cash.p.terminal.wallet.AdapterStoppedException
import cash.p.zcash.Balance
import cash.p.zcash.BroadcastResult
import cash.p.zcash.MempoolAmount
import cash.p.zcash.MempoolEvent
import cash.p.zcash.MempoolNote
import cash.p.zcash.Pool
import cash.p.zcash.PoolBalance
import cash.p.zcash.SyncStage
import cash.p.zcash.SyncState
import cash.p.zcash.Transaction
import cash.p.zcash.TransparentCoverage
import cash.p.zcash.ZcashException
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.core.storage.LogEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

private const val DRAIN_TIMEOUT_MS = 30_000L
private const val DB_ACCOUNT_ID = 7
private const val TXID = "5f2c"
private const val MEMO = "for the coffee"
private const val RECEIVED = 120_000L
private const val MINED_HEIGHT = 2_500_000
private const val BROADCAST_HEIGHT = 3_428_200
private val RAW = byteArrayOf(1, 2, 3)
private val ACCEPTED = BroadcastResult(errorCode = 0, message = TXID)
private const val CLEANUP_MS = 1_000L
private const val SERVER_URL = "https://zec.rocks:443"
private const val CREDENTIAL_MESSAGE =
    "connection failed: https://user:secret@upstream.example/v2/abcdef?token=sekrit"

// Mirrors the session's own constants: the tests assert the contract they define.
private const val UNCONFIRMED_TTL_MS = 60 * 60 * 1000L
private val FIRST_RETRY_MS = zcashRestartDelayFor(attempt = 1, baseMs = 5_000L, maxMs = 60_000L)

/**
 * A minimal stand-in for the SDK's own coverage record: a walk only ever widens [gap]. Shared
 * (not private) so [ZcashSessionManagerTest] can drive the same realistic record across a
 * session reopen, where a session-local field alone could not prove the memo survives.
 */
internal class FakeCoverage(var certified: Boolean = false, var gap: Int = 0, var trim: Long = 0)

/** Wires [wallet]'s coverage read and walk call to a shared, mutable [state]. */
internal fun stubDiscovery(wallet: ZcashWallet, state: FakeCoverage) {
    coEvery { wallet.transparentCoverage(any()) } answers {
        TransparentCoverage(certified = state.certified, gap = state.gap, trim = state.trim)
    }
    coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers {
        state.gap = maxOf(state.gap, secondArg())
        state.certified = true
        0
    }
}

internal fun zcashWalletMock(
    mempoolEvents: MutableSharedFlow<MempoolEvent> = MutableSharedFlow(extraBufferCapacity = 8),
) = mockk<ZcashWallet>(relaxed = true) {
    every { mempool() } returns mempoolEvents
    coEvery { balance(any(), any()) } returns PoolBalance(emptyMap())
    coEvery { transactions(any()) } returns emptyList<Transaction>()
    coEvery { latestHeight() } returns 0
}

internal fun TestScope.zcashSession(
    wallet: ZcashWallet,
    networkPaused: Boolean = false,
    supportsTransparent: Boolean = true,
    deepSweepRequired: Boolean = true,
    discovery: ZcashDiscoveryState = ZcashDiscoveryState(),
    logger: AppLogger = mockk(relaxed = true),
    networkErrorTracker: NetworkErrorTracker = mockk(relaxed = true),
) = ZcashSession(
    accountId = "account",
    wallet = wallet,
    dbAccountId = DB_ACCOUNT_ID,
    networkPaused = networkPaused,
    dispatcherProvider = TestDispatcherProvider(
        dispatcher = StandardTestDispatcher(testScheduler),
        applicationScope = backgroundScope,
    ),
    supportsTransparent = supportsTransparent,
    deepSweepRequired = deepSweepRequired,
    discovery = discovery,
    diagnostics = ZcashSessionDiagnostics(logger, networkErrorTracker, "account", SERVER_URL),
)

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
class ZcashSessionTest {

    private val mempoolEvents = MutableSharedFlow<MempoolEvent>(extraBufferCapacity = 8)

    private val wallet = zcashWalletMock(mempoolEvents)

    private val logger = mockk<AppLogger>(relaxed = true)
    private val networkErrorTracker = mockk<NetworkErrorTracker>(relaxed = true)

    private fun TestScope.session(networkPaused: Boolean = false) = zcashSession(
        wallet,
        networkPaused = networkPaused,
        logger = logger,
        networkErrorTracker = networkErrorTracker,
    )

    private suspend fun TestScope.syncFailingWith(error: Throwable) {
        every { wallet.sync(any()) } returns flow { throw error }
        session().sync()
    }

    private fun assertAdapterStopped(state: SyncState) {
        assertTrue("$state", (state as? SyncState.Failed)?.error is AdapterStoppedException)
    }

    private fun unconfirmed(value: Long, txid: String = TXID) = MempoolEvent.Unconfirmed(
        txid = txid,
        amounts = listOf(MempoolAmount(account = DB_ACCOUNT_ID, value = value)),
        notes = listOf(MempoolNote(account = DB_ACCOUNT_ID, value = value, pool = Pool.ORCHARD, memo = MEMO)),
        size = 512,
    )

    private fun mined(txid: String = TXID) = Transaction(
        id = 1,
        txid = txid,
        height = MINED_HEIGHT,
        time = 1_700_000_000,
        value = RECEIVED,
        memo = MEMO,
        fee = 0,
        totalReceived = RECEIVED,
        isChange = false,
        recipient = null,
    )

    @Test
    fun withOperation_afterClose_returnsUnavailable() = runTest {
        val session = session()
        advanceUntilIdle()

        assertTrue(session.drain(DRAIN_TIMEOUT_MS))
        session.close()

        assertEquals(ZcashSessionResult.Unavailable, session.withOperation { 42 })
    }

    @Test
    fun drain_operationInFlight_waitsForItToFinish() = runTest {
        val session = session()
        advanceUntilIdle()
        val started = CompletableDeferred<Unit>()
        var finished = false
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                delay(1_000)
                finished = true
            }
        }
        started.await()

        assertTrue(session.drain(DRAIN_TIMEOUT_MS))

        assertTrue(finished)
    }

    @Test
    fun drain_operationOutlastsTimeout_sessionReturnsToService() = runTest {
        val session = session()
        advanceUntilIdle()
        val started = CompletableDeferred<Unit>()
        backgroundScope.launch {
            session.withOperation {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()

        assertFalse(session.drain(DRAIN_TIMEOUT_MS))

        coVerify(exactly = 0) { wallet.close() }
        assertEquals(ZcashSessionResult.Success(7), session.withOperation { 7 })
        advanceUntilIdle()
        verify(atLeast = 2) { wallet.mempool() }
    }

    @Test
    fun close_afterDrain_closesTheWallet() = runTest {
        val session = session()
        advanceUntilIdle()

        assertTrue(session.drain(DRAIN_TIMEOUT_MS))
        session.close()

        coVerify(exactly = 1) { wallet.close() }
    }

    @Test
    fun drain_syncCompletesLater_keepsStoppedState() = runTest {
        val completion = CompletableDeferred<Unit>()
        every { wallet.sync(any()) } returns object : Flow<SyncState> {
            override suspend fun collect(collector: FlowCollector<SyncState>) {
                collector.emit(SyncState.Syncing(current = 100, target = 200))
                withContext(NonCancellable) {
                    completion.await()
                    collector.emit(SyncState.Synced)
                }
            }
        }
        val session = session()
        backgroundScope.launch { session.sync() }
        runCurrent()
        assertTrue(session.state.value.syncState is SyncState.Syncing)

        val draining = backgroundScope.launch { session.drain(DRAIN_TIMEOUT_MS) }
        runCurrent()
        completion.complete(Unit)
        runCurrent()

        assertTrue(draining.isCompleted)
        assertAdapterStopped(session.state.value.syncState)
        coVerify(exactly = 0) { wallet.balance(DB_ACCOUNT_ID, any()) }
    }

    @Test
    fun sync_advancingProgressThenSynced_doesNotDependOnNetworkHeightRefresh() = runTest {
        val target = MINED_HEIGHT + 100
        val freshBalance = PoolBalance(mapOf(Pool.ORCHARD to Balance(RECEIVED)))
        val freshTransactions = listOf(mined())
        every { wallet.sync(any()) } returns flow {
            emit(SyncState.Syncing(current = MINED_HEIGHT, target = target))
            emit(SyncState.Syncing(current = MINED_HEIGHT + 50, target = target))
            emit(SyncState.Synced)
        }
        coEvery { wallet.balance(DB_ACCOUNT_ID, any()) } returns freshBalance
        coEvery { wallet.transactions(DB_ACCOUNT_ID) } returns freshTransactions
        coEvery { wallet.latestHeight() } throws IOException("TLS handshake EOF")
        val session = session()
        val firstSynced = async {
            session.state.first { it.syncState is SyncState.Synced }
        }
        runCurrent()

        session.sync()
        val state = firstSynced.await()

        assertEquals(SyncState.Synced, state.syncState)
        assertEquals(freshBalance, state.balance)
        assertEquals(freshTransactions, state.transactions)
        assertEquals(target, state.latestHeight)
        coVerify(exactly = 1) { wallet.balance(DB_ACCOUNT_ID, any()) }
        coVerify(exactly = 1) { wallet.transactions(DB_ACCOUNT_ID) }
        coVerify(exactly = 0) { wallet.latestHeight() }
    }

    @Test
    fun drain_syncIgnoresNativeCancellation_cancelsTheCollector() = runTest {
        every { wallet.sync(any()) } returns flow {
            emit(SyncState.Connecting)
            awaitCancellation()
        }
        val session = session()
        backgroundScope.launch { session.sync() }
        runCurrent()

        val draining = backgroundScope.launch { session.drain(DRAIN_TIMEOUT_MS) }
        runCurrent()

        assertTrue(draining.isCompleted)
        assertAdapterStopped(session.state.value.syncState)
    }

    @Test
    fun drain_cancelAlreadyInProgress_waitsForNativeCancellation() = runTest {
        val cancellationStarted = CompletableDeferred<Unit>()
        val cancellationFinished = CompletableDeferred<Unit>()
        coEvery { wallet.cancelSync() } coAnswers {
            cancellationStarted.complete(Unit)
            cancellationFinished.await()
        }
        every { wallet.sync(any()) } returns flow {
            emit(SyncState.Connecting)
            awaitCancellation()
        }
        val session = session()
        backgroundScope.launch { session.sync() }
        runCurrent()

        val cancelling = backgroundScope.launch { session.cancelSync() }
        cancellationStarted.await()
        val draining = backgroundScope.launch { session.drain(DRAIN_TIMEOUT_MS) }
        runCurrent()

        assertFalse(draining.isCompleted)
        cancellationFinished.complete(Unit)
        runCurrent()
        assertTrue(cancelling.isCompleted)
        assertTrue(draining.isCompleted)
    }

    @Test
    fun drain_mempoolSubscribed_stopsTheSubscriptionBeforeReturning() = runTest {
        val session = session()
        advanceUntilIdle()
        assertEquals(1, mempoolEvents.subscriptionCount.value)

        assertTrue(session.drain(DRAIN_TIMEOUT_MS))

        assertEquals(0, mempoolEvents.subscriptionCount.value)
    }

    @Test
    fun transactions_incomingMempoolEvent_isVisibleBeforeMining() = runTest {
        val session = session()
        advanceUntilIdle()

        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()

        val transaction = session.state.value.transactions.single()
        assertEquals(TXID, transaction.txid)
        assertEquals(0, transaction.height)
        assertEquals(RECEIVED, transaction.value)
        assertEquals(MEMO, transaction.memo)
    }

    @Test
    fun transactions_minedTransactionWithSameTxid_doesNotDuplicateTheMempoolRow() = runTest {
        val session = session()
        advanceUntilIdle()
        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()
        coEvery { wallet.transactions(DB_ACCOUNT_ID) } returns listOf(mined())

        session.refresh()
        runCurrent()

        val transaction = session.state.value.transactions.single()
        assertEquals(MINED_HEIGHT, transaction.height)
    }

    @Test
    fun drain_publishesFailedWithAdapterStopped() = runTest {
        val session = session()
        advanceUntilIdle()

        assertTrue(session.drain(DRAIN_TIMEOUT_MS))

        assertAdapterStopped(session.state.value.syncState)
    }

    @Test
    fun broadcastReserved_drainStartsAfterReservation_stillBroadcasts() = runTest {
        val reserved = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        coEvery { wallet.reserveForBroadcast(any(), any(), any()) } coAnswers {
            reserved.complete(Unit)
            proceed.await()
        }
        coEvery { wallet.broadcast(any(), any(), any(), any()) } returns ACCEPTED
        val session = session()
        advanceUntilIdle()
        val sending = async { session.broadcastReserved(RAW, BROADCAST_HEIGHT, requireOwnInputs = true) }
        reserved.await()

        val draining = async { session.drain(DRAIN_TIMEOUT_MS) }
        runCurrent()
        assertFalse(draining.isCompleted)
        proceed.complete(Unit)

        assertEquals(ZcashSessionResult.Success(ACCEPTED), sending.await())
        assertTrue(draining.await())
        coVerify(exactly = 1) { wallet.reserveForBroadcast(DB_ACCOUNT_ID, RAW, true) }
        coVerify(exactly = 1) { wallet.broadcast(DB_ACCOUNT_ID, RAW, BROADCAST_HEIGHT, true) }
    }

    @Test
    fun broadcastReserved_whileDraining_returnsUnavailableWithoutReserving() = runTest {
        val session = session()
        advanceUntilIdle()
        assertTrue(session.drain(DRAIN_TIMEOUT_MS))

        val result = session.broadcastReserved(RAW, BROADCAST_HEIGHT, requireOwnInputs = true)

        assertEquals(ZcashSessionResult.Unavailable, result)
        coVerify(exactly = 0) { wallet.reserveForBroadcast(any(), any(), any()) }
        coVerify(exactly = 0) { wallet.broadcast(any(), any(), any(), any()) }
    }

    @Test
    fun broadcastReserved_reserveFails_throwsNotBroadcast() = runTest {
        val failure = ZcashException("inputs already reserved")
        coEvery { wallet.reserveForBroadcast(any(), any(), any()) } throws failure
        val session = session()

        val thrown = assertFailsWith<NotBroadcastException> {
            session.broadcastReserved(RAW, BROADCAST_HEIGHT, requireOwnInputs = true)
        }

        assertEquals(failure, thrown.cause)
        coVerify(exactly = 0) { wallet.broadcast(any(), any(), any(), any()) }
    }

    @Test
    fun broadcastReserved_forwardsRequireOwnInputsToBothNativeCalls() = runTest {
        coEvery { wallet.broadcast(any(), any(), any(), any()) } returns ACCEPTED
        val session = session()

        session.broadcastReserved(RAW, BROADCAST_HEIGHT, requireOwnInputs = false)

        coVerifyOrder {
            wallet.reserveForBroadcast(DB_ACCOUNT_ID, RAW, false)
            wallet.balance(DB_ACCOUNT_ID, any())
            wallet.broadcast(DB_ACCOUNT_ID, RAW, BROADCAST_HEIGHT, false)
        }
    }

    @Test
    fun transactions_epochEvent_keepsTheStillUnminedTransaction() = runTest {
        val session = session()
        advanceUntilIdle()
        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()

        mempoolEvents.emit(MempoolEvent.Epoch(MINED_HEIGHT))
        runCurrent()

        assertEquals(TXID, session.state.value.transactions.single().txid)
        assertEquals(MINED_HEIGHT, session.state.value.latestHeight)
    }

    @Test
    fun transactions_unconfirmedTtlExpires_dropsTheTransaction() = runTest {
        val session = session()
        advanceUntilIdle()
        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()

        advanceTimeBy(UNCONFIRMED_TTL_MS)
        assertEquals(1, session.state.value.transactions.size)

        advanceTimeBy(1)
        assertTrue(session.state.value.transactions.isEmpty())
    }

    @Test
    fun transactions_ownOutgoingEvent_isNotShownInHistory() = runTest {
        val session = session()
        advanceUntilIdle()

        mempoolEvents.emit(unconfirmed(-RECEIVED))
        runCurrent()

        assertTrue(session.state.value.transactions.isEmpty())
    }

    @Test
    fun transactions_unconfirmedEvent_isNotPersisted() = runTest {
        val session = session()
        advanceUntilIdle()

        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()

        assertEquals(1, session.state.value.transactions.size)
        verify(exactly = 0) { wallet.sync(any()) }
        coVerify(exactly = 0) { wallet.transactions(any()) }
    }

    @Test
    fun pauseMempool_networkPaused_stopsTheSubscriptionUntilResumed() = runTest {
        val session = session()
        advanceUntilIdle()
        assertEquals(1, mempoolEvents.subscriptionCount.value)

        session.pauseMempool()
        assertEquals(0, mempoolEvents.subscriptionCount.value)

        session.resumeMempool()
        advanceUntilIdle()
        assertEquals(1, mempoolEvents.subscriptionCount.value)
    }

    @Test
    fun mempool_subscriptionBreaks_resubscribesAfterBackoff() = runTest {
        every { wallet.mempool() } returnsMany listOf(flow { throw IOException("stream broken") }, mempoolEvents)
        session()

        runCurrent()
        verify(exactly = 1) { wallet.mempool() }

        advanceTimeBy(FIRST_RETRY_MS)
        verify(exactly = 1) { wallet.mempool() }

        advanceTimeBy(1)
        verify(exactly = 2) { wallet.mempool() }
        assertEquals(1, mempoolEvents.subscriptionCount.value)
    }

    @Test
    fun transactions_unconfirmedEventForAnotherAccount_isIgnored() = runTest {
        val session = session()
        advanceUntilIdle()

        mempoolEvents.emit(
            MempoolEvent.Unconfirmed(
                txid = TXID,
                amounts = listOf(MempoolAmount(account = DB_ACCOUNT_ID + 1, value = RECEIVED)),
                notes = emptyList(),
                size = 512,
            )
        )
        runCurrent()

        assertTrue(session.state.value.transactions.isEmpty())
    }

    @Test
    fun init_networkPaused_doesNotSubscribeUntilResumed() = runTest {
        val session = session(networkPaused = true)
        advanceUntilIdle()

        assertEquals(0, mempoolEvents.subscriptionCount.value)

        session.resumeMempool()
        advanceUntilIdle()

        assertEquals(1, mempoolEvents.subscriptionCount.value)
    }

    @Test
    fun resumeMempool_previousSubscriptionStillClosing_startsExactlyOneAfterIt() = runTest {
        var active = 0
        var peak = 0
        every { wallet.mempool() } returns flow {
            active++
            peak = maxOf(peak, active)
            try {
                awaitCancellation()
            } finally {
                // A native reader stays busy for a while after cancellation.
                withContext(NonCancellable) {
                    delay(CLEANUP_MS)
                    active--
                }
            }
        }
        val session = session()
        advanceUntilIdle()

        launch { session.pauseMempool() }
        runCurrent()
        launch { session.resumeMempool() }
        advanceUntilIdle()

        assertEquals(1, peak)
        assertEquals(1, active)
        verify(exactly = 2) { wallet.mempool() }
    }

    @Test
    fun drain_mempoolCleanupOutlastsTimeout_resubscribesExactlyOnce() = runTest {
        var active = 0
        var peak = 0
        every { wallet.mempool() } returns flow {
            active++
            peak = maxOf(peak, active)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    delay(CLEANUP_MS)
                    active--
                }
            }
        }
        val session = session()
        advanceUntilIdle()

        var drained = true
        val draining = launch { drained = session.drain(CLEANUP_MS / 2) }
        advanceTimeBy(CLEANUP_MS / 2 + 1)
        // The recovery join must not extend the timeout the drain promised.
        assertTrue(draining.isCompleted)
        launch { session.resumeMempool() }
        advanceUntilIdle()

        assertFalse(drained)
        assertEquals(1, peak)
        assertEquals(1, active)
        verify(exactly = 2) { wallet.mempool() }
    }

    @Test
    fun transactions_minedRowDisappears_doesNotRestoreTheMempoolRow() = runTest {
        val session = session()
        advanceUntilIdle()
        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()
        coEvery { wallet.transactions(DB_ACCOUNT_ID) } returns listOf(mined())
        session.refresh()

        coEvery { wallet.transactions(DB_ACCOUNT_ID) } returns emptyList()
        session.refresh()
        runCurrent()

        assertTrue(session.state.value.transactions.isEmpty())
    }

    @Test
    fun transactions_txidReannouncedAfterReorg_survivesTheFirstIncarnationsTtl() = runTest {
        val session = session()
        advanceUntilIdle()
        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()
        coEvery { wallet.transactions(DB_ACCOUNT_ID) } returns listOf(mined())
        session.refresh()
        coEvery { wallet.transactions(DB_ACCOUNT_ID) } returns emptyList()
        session.refresh()
        advanceTimeBy(UNCONFIRMED_TTL_MS / 2)

        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()
        advanceTimeBy(UNCONFIRMED_TTL_MS / 2 + 1)

        assertEquals(TXID, session.state.value.transactions.single().txid)
    }

    @Test
    fun transactions_eventForAnAlreadyMinedTxid_isNotRemembered() = runTest {
        coEvery { wallet.transactions(DB_ACCOUNT_ID) } returns listOf(mined())
        val session = session()
        advanceUntilIdle()
        session.refresh()

        mempoolEvents.emit(unconfirmed(RECEIVED))
        runCurrent()
        coEvery { wallet.transactions(DB_ACCOUNT_ID) } returns emptyList()
        session.refresh()
        runCurrent()

        assertTrue(session.state.value.transactions.isEmpty())
    }

    @Test
    fun collectSync_syncing_logsStartTargetAndDone() = runTest {
        every { wallet.sync(any()) } returns flow {
            emit(SyncState.Syncing(current = MINED_HEIGHT, target = MINED_HEIGHT + 100))
            emit(SyncState.Synced)
        }

        session().sync()

        verify(exactly = 1) { logger.info("sync: start gen=0") }
        verify(exactly = 1) { logger.info("sync: target=${MINED_HEIGHT + 100} current=$MINED_HEIGHT") }
        verify(exactly = 1) { logger.info(match { it.startsWith("sync: done target=${MINED_HEIGHT + 100} elapsed=") }) }
    }

    @Test
    fun collectSync_failed_writesStageAndCategory() = runTest {
        val error = ZcashException("tls handshake eof", stage = SyncStage.SCAN)

        syncFailingWith(error)

        verify(exactly = 1) { logger.warning(match { it.startsWith("sync failed stage=SCAN category=TLS: ") }) }
    }

    @Test
    fun collectSync_failedWithoutStage_writesUnknown() = runTest {
        val error = IOException("reset")

        syncFailingWith(error)

        verify(exactly = 1) { logger.warning(match { it.startsWith("sync failed stage=unknown category=unknown: ") }) }
    }

    @Test
    fun collectSync_networkFailure_recordsNetworkError() = runTest {
        val error = ZcashException("connection refused", stage = SyncStage.SCAN)

        syncFailingWith(error)

        verify(exactly = 1) {
            networkErrorTracker.record(
                BlockchainType.Zcash,
                "account",
                match {
                    it.method == "sync:SCAN" && it.url == SERVER_URL && it.host == "zec.rocks" &&
                        it.throwable === error
                },
            )
        }
    }

    @Test
    fun collectSync_failedStateEmittedByTheSdk_recordsNetworkError() = runTest {
        val error = ZcashException("deadline has elapsed", stage = SyncStage.SCAN)
        every { wallet.sync(any()) } returns flow { emit(SyncState.Failed(error)) }

        session().sync()

        verify(exactly = 1) { networkErrorTracker.record(BlockchainType.Zcash, "account", any()) }
    }

    @Test
    fun collectSync_databaseFailure_doesNotRecordNetworkError() = runTest {
        syncFailingWith(ZcashException("sqlite database is locked", stage = SyncStage.SCAN))

        verify(exactly = 0) { networkErrorTracker.record(any(), any(), any()) }
    }

    @Test
    fun collectSync_targetStageOtherCategory_recordsNetworkError() = runTest {
        syncFailingWith(ZcashException("unexpected failure", stage = SyncStage.TARGET))

        verify(exactly = 1) {
            networkErrorTracker.record(BlockchainType.Zcash, "account", match { it.method == "sync:TARGET" })
        }
    }

    @Test
    fun collectSync_ioFailureOutsideTheSdk_recordsNetworkErrorWithUnknownStage() = runTest {
        syncFailingWith(IOException("reset"))

        verify(exactly = 1) {
            networkErrorTracker.record(BlockchainType.Zcash, "account", match { it.method == "sync:unknown" })
        }
    }

    @Test
    fun collectSync_otherFailure_doesNotRecordNetworkError() = runTest {
        syncFailingWith(IllegalStateException("bug"))

        verify(exactly = 0) { networkErrorTracker.record(any(), any(), any()) }
    }

    @Test
    fun mempool_sameErrorRepeatedly_warnsOncePerErrorClass() = runTest {
        every { wallet.mempool() } returns flow { throw IOException("down") }
        val session = session()

        advanceTimeBy(5 * 60_000L)
        // The retry loop never ends on its own, and runTest would drain virtual time forever.
        session.drain(DRAIN_TIMEOUT_MS)

        verify(atLeast = 2) { wallet.mempool() }
        verify(exactly = 1) { logger.warning(match { it.startsWith("Mempool subscription failed: ") }) }
    }

    @Test
    fun collectSync_failedWithCredentialsInMessage_logsSanitizedText() = runTest {
        val appLog = CapturedAppLog()
        every { wallet.sync(any()) } returns flow { throw ZcashException(CREDENTIAL_MESSAGE, stage = SyncStage.SCAN) }

        zcashSession(wallet, logger = zcashAppLogger("account")).sync()

        appLog.awaitEntry { it.message.startsWith("sync failed") }.assertSanitized()
    }

    @Test
    fun mempool_subscriptionErrorWithCredentialsInMessage_logsSanitizedText() = runTest {
        val appLog = CapturedAppLog()
        every { wallet.mempool() } returns flow { throw IOException(CREDENTIAL_MESSAGE) }
        val session = zcashSession(wallet, logger = zcashAppLogger("account"))
        advanceTimeBy(1_000L)
        session.drain(DRAIN_TIMEOUT_MS)

        appLog.awaitEntry { it.message.startsWith("Mempool subscription failed") }.assertSanitized()
    }

    @Test
    fun collectSync_mempoolEpochBeforeSynced_doneLineKeepsThePassTarget() = runTest {
        val finish = CompletableDeferred<Unit>()
        every { wallet.sync(any()) } returns flow {
            emit(SyncState.Syncing(current = MINED_HEIGHT, target = MINED_HEIGHT))
            finish.await()
            emit(SyncState.Synced)
        }
        val session = session()
        backgroundScope.launch { session.sync() }
        runCurrent()

        mempoolEvents.emit(MempoolEvent.Epoch(MINED_HEIGHT + 1))
        runCurrent()
        finish.complete(Unit)
        runCurrent()

        verify(exactly = 1) { logger.info(match { it.startsWith("sync: done target=$MINED_HEIGHT elapsed=") }) }
    }

    private fun LogEntry.assertSanitized() {
        assertTrue(message, "upstream.example" in message)
        listOf("user:secret", "sekrit", "abcdef").forEach { assertFalse(message, it in message) }
    }
}
