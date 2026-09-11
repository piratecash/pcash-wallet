package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.zcash.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val LONG_ENOUGH_FOR_A_FULL_TURN_MS = 120_000L
private const val FIRST_RESTART_DELAY_MS = 15_000L

class ZcashSyncSchedulerTest {

    private val offlineModeManager = mockk<OfflineModeManager>()
    private val sessionState = MutableStateFlow(ZcashSessionState(syncState = SyncState.Synced))
    private val session = mockk<ZcashSession>(relaxed = true)

    @Before
    fun setUp() {
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns false
        every { session.accountId } returns "account"
        every { session.state } returns sessionState
        coEvery { session.sync() } returns ZcashSessionResult.Success(Unit)
    }

    private fun TestScope.scheduler() = ZcashSyncScheduler(
        offlineModeManager = offlineModeManager,
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
}
