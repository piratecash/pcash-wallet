package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.ZcashSessionDiagnostics
import cash.p.terminal.core.adapters.zcash.ZcashTransparentIndexStore
import cash.p.terminal.core.adapters.zcash.server.ZcashServerProber
import cash.p.terminal.core.adapters.zcash.server.ZcashServerSelector
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.zcash.BlockDownload
import cash.p.zcash.PoolBalance
import cash.p.zcash.SyncState
import cash.p.zcash.Transaction
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.IBackgroundManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private const val LONG_ENOUGH_FOR_A_FULL_TURN_MS = 120_000L
private const val FIRST_RESTART_DELAY_MS = 15_000L
private const val POLL_INTERVAL_MS = 30_000L
private const val DISCOVERY_DEBOUNCE_MS = 30_000L
private const val DISCOVERY_RETRY_MS = 3 * 60 * 1000L
private const val QUIET_RETRY_MIN_MS = 4_000L
private const val QUIET_RETRY_MAX_MS = 6_000L
private const val SEARCH_TURN_BUDGET_MS = 20_000L
private const val CHUNK_UPTIME_MS = 8_000L
private val SYNCED = ZcashSessionResult.Success(ZcashPassOutcome.Synced)

class ZcashSyncSchedulerTest {

    private val offlineModeManager = mockk<OfflineModeManager>()
    private val backgroundManager = mockk<IBackgroundManager>()
    private val uptimeProvider = mockk<UptimeProvider>()
    private val foregroundEpoch = MutableStateFlow(0)
    private val session = mockk<ZcashSession>(relaxed = true)
    private val selector = mockk<ZcashServerSelector>(relaxed = true)
    private val prober = mockk<ZcashServerProber>(relaxed = true)
    private val transparentIndexStore = mockk<ZcashTransparentIndexStore>(relaxed = true)
    private val addressSearch = mockk<ZcashAddressSearch>()
    private val blockDownloadPolicy = mockk<ZcashBlockDownloadPolicy> {
        every { blockDownload } returns BlockDownload()
    }

    @Before
    fun setUp() {
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns false
        // Discovery off by default: most of this file's tests are about the sync cadence, not
        // discovery, and the account here never supports transparent addresses.
        every { backgroundManager.inForeground } returns false
        every { backgroundManager.foregroundEpoch } returns foregroundEpoch
        every { uptimeProvider.uptime } returns 0L
        every { session.accountId } returns "account"
        every { session.discovery } returns ZcashDiscoveryState()
        coEvery { session.sync() } returns SYNCED
        coEvery { addressSearch.runStep(any()) } returns ZcashAddressSearchStep.IDLE
    }

    private fun TestScope.scheduler() = ZcashSyncScheduler(
        offlineModeManager = offlineModeManager,
        backgroundManager = backgroundManager,
        uptimeProvider = uptimeProvider,
        selector = selector,
        prober = prober,
        blockDownloadPolicy = blockDownloadPolicy,
        transparentIndexStore = transparentIndexStore,
        addressSearch = addressSearch,
        dispatcherProvider = TestDispatcherProvider(
            dispatcher = StandardTestDispatcher(testScheduler),
            applicationScope = backgroundScope,
        ),
    )

    @Test
    fun enqueue_networkPaused_skipsTheSyncPass() = runTest {
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns true
        val scheduler = scheduler()

        scheduler.enqueue(session)
        advanceTimeBy(1_000)

        coVerify(exactly = 0) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun enqueue_syncPass_usesThePolicyBlockDownload() = runTest {
        val blockDownload = BlockDownload(maxStreams = 3, bufferMegabytes = 24)
        every { blockDownloadPolicy.blockDownload } returns blockDownload
        coEvery { session.sync(any()) } returns SYNCED
        val scheduler = scheduler()

        scheduler.enqueue(session)
        advanceTimeBy(1_000)

        coVerify(exactly = 1) { session.sync(blockDownload) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun enqueue_twoSessions_runsThemOneAtATime() = runTest {
        val other = mockk<ZcashSession>(relaxed = true)
        every { other.accountId } returns "other"
        every { other.discovery } returns ZcashDiscoveryState()
        val blocked = CompletableDeferred<Unit>()
        coEvery { session.sync() } coAnswers {
            blocked.await()
            SYNCED
        }
        val scheduler = scheduler()

        scheduler.enqueue(session)
        scheduler.enqueue(other)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)

        coVerify(exactly = 1) { session.sync() }
        coVerify(exactly = 0) { other.sync() }
        blocked.complete(Unit)
        scheduler.remove(session)
        scheduler.remove(other)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_beforeThePass_letsTheSelectorEvaluate() = runTest {
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerifyOrder {
            selector.evaluateIfDue()
            session.sync()
        }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_passFinished_reportsTheServerThePassEnteredOnAndItsStreamBreaks() = runTest {
        every { session.serverUrl } returns "https://a.zec:443"
        every { session.passServerUrl } returns "https://b.zec:443"
        every { prober.streamBreaks() } returnsMany listOf(5L, 7L)
        val failed = ZcashPassOutcome.Failed(IOException("unexpected EOF"), published = true)
        coEvery { session.sync() } returns ZcashSessionResult.Success(failed)
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify { selector.onPassFinished("https://b.zec:443", failed, 2) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_networkPausedDuringThePass_doesNotReportToTheSelector() = runTest {
        var paused = false
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } answers { paused }
        coEvery { session.sync() } coAnswers {
            paused = true
            ZcashSessionResult.Success(ZcashPassOutcome.Failed(IOException("unexpected EOF"), published = false))
        }
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 0) { selector.onPassFinished(any(), any(), any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_quietFailure_nextPassWaitsForTheSelectorDecision() = runTest {
        val decided = CompletableDeferred<Unit>()
        coEvery { selector.onPassFinished(any(), any(), any()) } coAnswers { decided.await() }
        coEvery { session.sync() } returns ZcashSessionResult.Success(
            ZcashPassOutcome.Failed(IOException("unexpected EOF"), published = false)
        )
        val scheduler = scheduler()

        scheduler.enqueue(session)
        advanceTimeBy(QUIET_RETRY_MAX_MS + 1)
        coVerify(exactly = 1) { session.sync() }

        decided.complete(Unit)
        advanceTimeBy(QUIET_RETRY_MAX_MS + 1)
        coVerify(exactly = 2) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    /** Corruption gets no special path: it retries on the ordinary backoff like any failure. */
    @Test
    fun runTick_databaseCorruption_keepsTheSessionAndRetriesAfterTheBackoff() = runTest {
        coEvery { session.sync() } returns ZcashSessionResult.Success(
            ZcashPassOutcome.Failed(RuntimeException("database disk image is malformed"), published = true)
        )
        val scheduler = scheduler()

        scheduler.enqueue(session)
        advanceTimeBy(FIRST_RESTART_DELAY_MS)
        coVerify(exactly = 1) { session.sync() }

        advanceTimeBy(1)
        coVerify(exactly = 2) { session.sync() }

        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_hiddenFailure_retriesWithinTheQuietWindow() = runTest {
        coEvery { session.sync() } returns ZcashSessionResult.Success(
            ZcashPassOutcome.Failed(IOException("unexpected EOF"), published = false)
        )
        val scheduler = scheduler()

        scheduler.enqueue(session)
        advanceTimeBy(QUIET_RETRY_MIN_MS)
        coVerify(exactly = 1) { session.sync() }

        advanceTimeBy(QUIET_RETRY_MAX_MS - QUIET_RETRY_MIN_MS)
        runCurrent()
        coVerify(exactly = 2) { session.sync() }
        verify(exactly = 0) { session.syncAttempts = any() }

        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_networkFailures_firstStaysHiddenAndRetriesQuicklyThenSecondIsShown() = runTest {
        val wallet = zcashWalletMock()
        every { wallet.sync(any()) } returns flow { throw IOException("unexpected EOF") }
        val realSession = zcashSession(wallet, networkPaused = true)
        val seen = mutableListOf<SyncState>()
        backgroundScope.launch { realSession.state.collect { seen += it.syncState } }
        val scheduler = scheduler()

        scheduler.enqueue(realSession)
        advanceTimeBy(QUIET_RETRY_MIN_MS)
        verify(exactly = 1) { wallet.sync(any()) }
        assertTrue("$seen", seen.none { it is SyncState.Failed })

        advanceTimeBy(QUIET_RETRY_MAX_MS - QUIET_RETRY_MIN_MS)
        runCurrent()
        verify(exactly = 2) { wallet.sync(any()) }
        assertTrue(realSession.state.value.syncState is SyncState.Failed)

        scheduler.remove(realSession)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_cancelled_pollsWithoutTouchingTheBackoff() = runTest {
        coEvery { session.sync() } returns ZcashSessionResult.Success(ZcashPassOutcome.Cancelled)
        val scheduler = scheduler()

        scheduler.enqueue(session)
        advanceTimeBy(POLL_INTERVAL_MS)
        coVerify(exactly = 1) { session.sync() }

        advanceTimeBy(1)
        coVerify(exactly = 2) { session.sync() }
        verify(exactly = 0) { session.syncAttempts = any() }

        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_synced_resetsTheBackoff() = runTest {
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        verify(exactly = 1) { session.syncAttempts = 0 }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun wakeUp_duringThePollWait_startsTheNextTurnAtOnce() = runTest {
        val scheduler = scheduler()
        scheduler.enqueue(session)
        advanceTimeBy(1_000)
        coVerify(exactly = 1) { session.sync() }

        scheduler.wakeUp()
        runCurrent()

        coVerify(exactly = 2) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    // --- discovery cadence ---

    @Test
    fun runTick_discoversBeforeTheFirstSync() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } returns true
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerifyOrder {
            session.discoverForEpoch(any())
            session.sync()
        }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_firstTurn_preparesTheIndexFirstAndRaisesItAfterTheWalk() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } returns true
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerifyOrder {
            transparentIndexStore.prepare(session)
            session.discoverForEpoch(any())
            transparentIndexStore.raiseFromHistory(session)
            session.sync()
        }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_indexPreparationThrows_skipsSyncUntilItSucceeds() = runTest {
        coEvery { transparentIndexStore.prepare(session) } throws IOException("boom") andThen Unit
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 0) { session.discoverForEpoch(any()) }
        coVerify(exactly = 0) { session.sync() }

        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        coVerify(exactly = 1) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_searchPending_checksAChunkBeforeSyncAndTurnsAgainAtOnce() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } returns true
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        // The first chunk spends the whole budget, so the turn ends with the search still pending.
        coEvery { addressSearch.runStep(session) } coAnswers {
            currentUptime += SEARCH_TURN_BUDGET_MS
            ZcashAddressSearchStep.MORE_PENDING
        } andThen ZcashAddressSearchStep.IDLE
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerifyOrder {
            transparentIndexStore.prepare(session)
            session.discoverForEpoch(any())
            transparentIndexStore.raiseFromHistory(session)
            addressSearch.runStep(session)
            session.sync()
        }
        coVerify(exactly = 2) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_searchPending_runsChunksUntilTheBudgetThenSyncs() = runTest {
        every { backgroundManager.inForeground } returns true
        val events = mutableListOf<String>()
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        coEvery { addressSearch.runStep(session) } coAnswers {
            events += "chunk"
            currentUptime += CHUNK_UPTIME_MS
            if (events.count { it == "chunk" } < 4) ZcashAddressSearchStep.MORE_PENDING else ZcashAddressSearchStep.IDLE
        }
        coEvery { session.sync() } coAnswers {
            events += "sync"
            SYNCED
        }
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        assertEquals(listOf("chunk", "chunk", "chunk", "sync", "chunk", "sync"), events)
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_searchFinishesWithinTheBudget_syncsOnceAndWaitsThePoll() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { addressSearch.runStep(session) } returnsMany listOf(
            ZcashAddressSearchStep.MORE_PENDING,
            ZcashAddressSearchStep.MORE_PENDING,
            ZcashAddressSearchStep.CHECKED,
        )
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()
        advanceTimeBy(POLL_INTERVAL_MS - 1)
        runCurrent()

        coVerify(exactly = 3) { addressSearch.runStep(session) }
        coVerify(exactly = 1) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_searchStoppedBetweenChunks_endsTheLoopAndSyncs() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { addressSearch.runStep(session) } returns ZcashAddressSearchStep.MORE_PENDING andThen
            ZcashAddressSearchStep.IDLE
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 2) { addressSearch.runStep(session) }
        coVerify(exactly = 1) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_backgroundedBetweenChunks_stopsSearchingAndSyncs() = runTest {
        var inForeground = true
        every { backgroundManager.inForeground } answers { inForeground }
        coEvery { addressSearch.runStep(session) } coAnswers {
            inForeground = false
            ZcashAddressSearchStep.MORE_PENDING
        }
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 1) { addressSearch.runStep(session) }
        coVerify(atLeast = 1) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_offlineBetweenChunks_stopsSearchingAndSkipsSync() = runTest {
        every { backgroundManager.inForeground } returns true
        var paused = false
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } answers { paused }
        coEvery { addressSearch.runStep(session) } coAnswers {
            paused = true
            ZcashAddressSearchStep.MORE_PENDING
        }
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 1) { addressSearch.runStep(session) }
        coVerify(exactly = 0) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_chunkFails_waitsTheNormalPollBeforeRetrying() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { addressSearch.runStep(session) } throws IOException("boom")
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 1) { session.sync() }
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()
        coVerify(exactly = 2) { addressSearch.runStep(session) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_backgrounded_runsNoSearchChunk() = runTest {
        coEvery { addressSearch.runStep(session) } returns ZcashAddressSearchStep.MORE_PENDING
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 0) { addressSearch.runStep(any()) }
        coVerify(exactly = 1) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_offlineArrivesDuringDiscovery_runsNoChunkAndNoSync() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { addressSearch.runStep(session) } returns ZcashAddressSearchStep.MORE_PENDING
        coEvery { session.discoverForEpoch(any()) } coAnswers {
            every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns true
            true
        }
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)

        coVerify(exactly = 1) { session.discoverForEpoch(any()) }
        coVerify(exactly = 0) { addressSearch.runStep(any()) }
        coVerify(exactly = 0) { session.sync() }
    }

    @Test
    fun runTick_indexPreparationFails_runsNoSearchChunk() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { transparentIndexStore.prepare(session) } throws IOException("boom")
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 0) { addressSearch.runStep(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_backgrounded_doesNotDiscoverEvenAfterIntervalExpires() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns false
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()
        currentUptime = DISCOVERY_RETRY_MS * 10
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)

        coVerify(exactly = 0) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
    }

    @Test
    fun runTick_withinTheDebounce_doesNotRediscoverEvenAfterAForeground() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } returns true
        val scheduler = scheduler()
        scheduler.enqueue(session)
        runCurrent()

        foregroundEpoch.value = 1
        currentUptime = DISCOVERY_DEBOUNCE_MS - 1
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        coVerify(exactly = 1) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_afterTheDebouncePasses_rediscoversFollowingAForeground() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } returns true
        val scheduler = scheduler()
        scheduler.enqueue(session)
        runCurrent()

        foregroundEpoch.value = 1
        currentUptime = DISCOVERY_DEBOUNCE_MS + 1
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        coVerify(exactly = 2) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_discoverySucceeded_asksAgainAfterTheDebounceWithoutANewForeground() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } returns true
        val scheduler = scheduler()
        scheduler.enqueue(session)
        runCurrent()

        currentUptime = DISCOVERY_DEBOUNCE_MS + 1
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        coVerify(exactly = 2) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_foregroundArrivesDuringTheWalk_leavesTheSessionDue() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns true
        val walkStarted = CompletableDeferred<Unit>()
        val releaseWalk = CompletableDeferred<Unit>()
        coEvery { session.discoverForEpoch(any()) } coAnswers {
            walkStarted.complete(Unit)
            releaseWalk.await()
            true
        }
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()
        walkStarted.await()
        foregroundEpoch.value = 1 // a foreground event lands mid-walk
        releaseWalk.complete(Unit)
        runCurrent()

        currentUptime = DISCOVERY_DEBOUNCE_MS + 1
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        coVerify(exactly = 2) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_networkPaused_skipsDiscovery() = runTest {
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns true
        every { backgroundManager.inForeground } returns true
        val scheduler = scheduler()

        scheduler.enqueue(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)

        coVerify(exactly = 0) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_networkPausedDuringTheWalk_doesNotStartTheSync() = runTest {
        every { backgroundManager.inForeground } returns true
        var paused = false
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } answers { paused }
        coEvery { session.discoverForEpoch(any()) } coAnswers {
            paused = true
            true
        }
        val scheduler = scheduler()

        scheduler.enqueue(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
        // Verify only after the loop is gone: a failed assertion before remove() leaves runTest spinning.
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)

        coVerify(exactly = 1) { session.discoverForEpoch(any()) }
        coVerify(exactly = 0) { session.sync() }
    }

    @Test
    fun runTick_discoveryThrows_doesNotStopTheLoopAndSyncStillRuns() = runTest {
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } throws IOException("boom")
        val scheduler = scheduler()

        scheduler.enqueue(session)
        runCurrent()

        coVerify(exactly = 1) { session.sync() }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_discoveryFailed_doesNotRetryBeforeTheRetryInterval() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } returns false
        val scheduler = scheduler()
        scheduler.enqueue(session)
        runCurrent()

        currentUptime = DISCOVERY_RETRY_MS - 1
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        coVerify(exactly = 1) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_discoveryFailed_retriesAfterTheRetryInterval() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns true
        coEvery { session.discoverForEpoch(any()) } returns false
        val scheduler = scheduler()
        scheduler.enqueue(session)
        runCurrent()

        currentUptime = DISCOVERY_RETRY_MS + 1
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        coVerify(exactly = 2) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_coverageInvalidatedAndTheTrimSettles_walksAgainWithoutAForegroundEvent() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns true
        val coverage = FakeCoverage()
        val zcashWallet = mockk<ZcashWallet>(relaxed = true) {
            coEvery { balance(any(), any()) } returns PoolBalance(emptyMap())
            coEvery { transactions(any()) } returns emptyList<Transaction>()
            coEvery { latestHeight() } returns 0
            every { sync(any()) } returns flowOf(SyncState.Synced)
        }
        stubDiscovery(zcashWallet, coverage)
        val realSession = ZcashSession(
            accountId = "account",
            wallet = zcashWallet,
            dbAccountId = 7,
            networkPaused = true,
            dispatcherProvider = TestDispatcherProvider(
                dispatcher = StandardTestDispatcher(testScheduler),
                applicationScope = backgroundScope,
            ),
            supportsTransparent = true,
            deepSweepRequired = true,
            discovery = ZcashDiscoveryState(),
            diagnostics = ZcashSessionDiagnostics(
                logger = mockk(relaxed = true),
                networkErrorTracker = mockk(relaxed = true),
                accountId = "account",
                serverUrl = "https://zec.rocks:443",
                failureReporter = mockk<ZcashDatabaseFailureReporter>(relaxed = true),
            ),
        )
        val scheduler = scheduler()

        scheduler.enqueue(realSession)
        runCurrent() // first turn: deep walk lands, epoch published

        // The very next sync() trims the walk's own note: certified flips false at a fixed generation.
        coverage.certified = false
        coverage.trim = 1
        currentUptime = DISCOVERY_DEBOUNCE_MS + 1
        advanceTimeBy(POLL_INTERVAL_MS) // second turn: first sight of the new trim, observes only
        runCurrent()

        currentUptime += DISCOVERY_DEBOUNCE_MS + 1
        advanceTimeBy(POLL_INTERVAL_MS) // third turn: same settled generation, walks again
        runCurrent()

        coVerify(exactly = 2) { zcashWallet.discoverTransparentAddresses(any(), any(), any()) }
        scheduler.remove(realSession)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }

    @Test
    fun runTick_deepSweepStillOwed_walkReturnsFalseAndRetriesAfterTheRetryInterval() = runTest {
        var currentUptime = 0L
        every { uptimeProvider.uptime } answers { currentUptime }
        every { backgroundManager.inForeground } returns true
        // A walk that succeeds but still leaves the gap short of the deep target reports "not
        // covered" (see ZcashSession.discoverForEpoch): the scheduler must treat that as failure.
        coEvery { session.discoverForEpoch(any()) } returns false
        val scheduler = scheduler()
        scheduler.enqueue(session)
        runCurrent()
        coVerify(exactly = 1) { session.discoverForEpoch(any()) }

        currentUptime = DISCOVERY_DEBOUNCE_MS + 1
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()
        coVerify(exactly = 1) { session.discoverForEpoch(any()) } // still within the retry interval

        currentUptime = DISCOVERY_RETRY_MS + 1
        advanceTimeBy(POLL_INTERVAL_MS)
        runCurrent()

        coVerify(exactly = 2) { session.discoverForEpoch(any()) }
        scheduler.remove(session)
        advanceTimeBy(LONG_ENOUGH_FOR_A_FULL_TURN_MS)
    }
}
