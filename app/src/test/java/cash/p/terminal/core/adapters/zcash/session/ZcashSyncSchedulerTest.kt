package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.zcash.PoolBalance
import cash.p.zcash.SyncState
import cash.p.zcash.Transaction
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.BackgroundManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

private const val LONG_ENOUGH_FOR_A_FULL_TURN_MS = 120_000L
private const val FIRST_RESTART_DELAY_MS = 15_000L
private const val POLL_INTERVAL_MS = 30_000L
private const val DISCOVERY_DEBOUNCE_MS = 30_000L
private const val DISCOVERY_RETRY_MS = 3 * 60 * 1000L

class ZcashSyncSchedulerTest {

    private val offlineModeManager = mockk<OfflineModeManager>()
    private val backgroundManager = mockk<BackgroundManager>()
    private val uptimeProvider = mockk<UptimeProvider>()
    private val foregroundEpoch = MutableStateFlow(0)
    private val sessionState = MutableStateFlow(ZcashSessionState(syncState = SyncState.Synced))
    private val session = mockk<ZcashSession>(relaxed = true)

    @Before
    fun setUp() {
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns false
        // Discovery off by default: most of this file's tests are about the sync cadence, not
        // discovery, and the account here never supports transparent addresses.
        every { backgroundManager.inForeground } returns false
        every { backgroundManager.foregroundEpoch } returns foregroundEpoch
        every { uptimeProvider.uptime } returns 0L
        every { session.accountId } returns "account"
        every { session.state } returns sessionState
        every { session.discovery } returns ZcashDiscoveryState()
        coEvery { session.sync() } returns ZcashSessionResult.Success(Unit)
    }

    private fun TestScope.scheduler() = ZcashSyncScheduler(
        offlineModeManager = offlineModeManager,
        backgroundManager = backgroundManager,
        uptimeProvider = uptimeProvider,
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
    fun enqueue_twoSessions_runsThemOneAtATime() = runTest {
        val other = mockk<ZcashSession>(relaxed = true)
        every { other.accountId } returns "other"
        every { other.state } returns MutableStateFlow(ZcashSessionState(syncState = SyncState.Synced))
        every { other.discovery } returns ZcashDiscoveryState()
        val blocked = CompletableDeferred<Unit>()
        coEvery { session.sync() } coAnswers {
            blocked.await()
            ZcashSessionResult.Success(Unit)
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

    /** Corruption gets no special path: it retries on the ordinary backoff like any failure. */
    @Test
    fun runTick_databaseCorruption_keepsTheSessionAndRetriesAfterTheBackoff() = runTest {
        sessionState.value = ZcashSessionState(
            syncState = SyncState.Failed(RuntimeException("database disk image is malformed"))
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
