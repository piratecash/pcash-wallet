package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerUserOperationsTest {

    private val operations = LedgerUserOperations()

    @Test
    fun endViewKeyExport_afterBegin_awaitingOnlyInBetween() = runTest {
        assertFalse(operations.awaitingViewKeyExport.first())

        operations.beginViewKeyExport()
        val awaitingInside = operations.awaitingViewKeyExport.first()
        operations.endViewKeyExport()

        assertTrue(awaitingInside)
        assertFalse(operations.awaitingViewKeyExport.first())
    }

    @Test
    fun endViewKeyExport_nested_awaitingUntilOutermostEnds() = runTest {
        operations.beginViewKeyExport()
        operations.beginViewKeyExport()

        operations.endViewKeyExport()
        assertTrue(operations.awaitingViewKeyExport.first())
        operations.endViewKeyExport()

        assertFalse(operations.awaitingViewKeyExport.first())
    }

    @Test
    fun run_blockCompletes_activeOnlyWhileBlockRuns() = runTest {
        assertFalse(operations.isActive)

        val activeInside = operations.run { operations.isActive }

        assertTrue(activeInside)
        assertFalse(operations.isActive)
    }

    @Test
    fun run_blockThrows_inactiveAfter() = runTest {
        assertFailsWith<IllegalStateException> { operations.run { error("device unplugged") } }

        assertFalse(operations.isActive)
    }

    @Test
    fun run_blockCancelled_inactiveAfter() = runTest {
        val job = launch { operations.run { awaitCancellation() } }
        runCurrent()
        assertTrue(operations.isActive)

        job.cancel()
        job.join()

        assertFalse(operations.isActive)
    }

    @Test
    fun run_nested_staysActiveUntilOutermostEnds() = runTest {
        val activeAfterInner = operations.run {
            operations.run {}
            operations.isActive
        }

        assertTrue(activeAfterInner)
        assertFalse(operations.isActive)
    }

    @Test
    fun reportYielded_duringNestedRun_emitsEachAppOnceAfterOutermostEnds() = runTest {
        val notices = collected(operations.yieldedNotices)

        operations.run {
            operations.run { operations.reportYielded(MONERO) }
            operations.reportYielded(MONERO)
            assertEquals(emptyList(), notices)
        }
        operations.run {}

        assertEquals(listOf(MONERO), notices)
    }

    @Test
    fun reportYielded_noRun_emitsImmediately() = runTest {
        val notices = collected(operations.yieldedNotices)

        operations.reportYielded(MONERO)

        assertEquals(listOf(MONERO), notices)
    }

    @Test
    fun remindWhileUsersWait_operationEndsBeforeFirstNotice_noNotice() = runTest {
        val noticeTimes = remindingWithNoticeTimes()

        launch { operations.run { delay(1_400) } }
        advanceUntilIdle()

        assertEquals(emptyList(), noticeTimes)
    }

    @Test
    fun remindWhileUsersWait_operationKeepsWaiting_firstNoticeAfterDelayThenEveryInterval() = runTest {
        val noticeTimes = remindingWithNoticeTimes()

        launch { operations.run { delay(25_000) } }
        advanceUntilIdle()

        assertEquals(listOf(1_500L, 11_500L, 21_500L), noticeTimes)
    }

    @Test
    fun remindWhileUsersWait_operationEndsThenLaterOneWaits_remindsLaterOneAfterDelay() = runTest {
        val noticeTimes = remindingWithNoticeTimes()

        launch { operations.run { delay(2_000) } }
        advanceTimeBy(5_000)
        launch { operations.run { delay(2_000) } }
        advanceUntilIdle()

        assertEquals(listOf(1_500L, 6_500L), noticeTimes)
    }

    @Test
    fun remindWhileUsersWait_cancelled_noMoreNotices() = runTest {
        val noticeTimes = waitNoticeTimes()
        val reminder = launch { operations.remindWhileUsersWait(SYNC_NOTICE) }
        launch { operations.run { delay(30_000) } }
        advanceTimeBy(2_000)

        reminder.cancel()
        advanceUntilIdle()

        assertEquals(listOf(1_500L), noticeTimes)
    }

    @Test
    fun waitNotices_collectedAfterReminderCancelled_dropsQueuedNotice() = runTest {
        val late = ManualDispatcher()
        val notices = collectedOn(late)
        val reminder = launch { operations.remindWhileUsersWait(SYNC_NOTICE) }
        launch { operations.run { delay(30_000) } }
        advanceTimeBy(2_000)

        reminder.cancel()
        reminder.join()
        late.runQueued()

        assertEquals(emptyList(), notices)
    }

    @Test
    fun waitNotices_collectedAfterUserOperationEnded_dropsQueuedNotice() = runTest {
        val late = ManualDispatcher()
        val notices = collectedOn(late)
        backgroundScope.launch { operations.remindWhileUsersWait(SYNC_NOTICE) }
        launch { operations.run { delay(2_000) } }
        advanceTimeBy(3_000)

        late.runQueued()

        assertEquals(emptyList(), notices)
    }

    @Test
    fun waitNotices_collectedWhileWaiting_deliversNotice() = runTest {
        val late = ManualDispatcher()
        val notices = collectedOn(late)
        backgroundScope.launch { operations.remindWhileUsersWait(SYNC_NOTICE) }
        launch { operations.run { delay(30_000) } }
        advanceTimeBy(2_000)

        late.runQueued()

        assertEquals(listOf(SYNC_NOTICE), notices)
    }

    /** The collector resumes only when [dispatcher] runs, i.e. after the emission was queued. */
    private fun TestScope.collectedOn(dispatcher: ManualDispatcher): List<LedgerWaitNotice> =
        mutableListOf<LedgerWaitNotice>().also { values ->
            backgroundScope.launch(dispatcher) { operations.waitNotices.collect { values += it } }
            dispatcher.runQueued()
        }

    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue += block
        }

        fun runQueued() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private fun TestScope.remindingWithNoticeTimes(): List<Long> = waitNoticeTimes().also {
        backgroundScope.launch { operations.remindWhileUsersWait(SYNC_NOTICE) }
    }

    private fun TestScope.waitNoticeTimes(): List<Long> = mutableListOf<Long>().also { times ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            operations.waitNotices.collect { times += currentTime }
        }
    }

    private fun <T> TestScope.collected(flow: Flow<T>): List<T> = mutableListOf<T>().also { values ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(values) }
    }

    private companion object {
        const val MONERO = "Monero"
        val SYNC_NOTICE = LedgerWaitNotice(MONERO, LedgerWaitReason.Sync)
    }
}

/** Records, for every exchange on its connections, whether a user operation was active at that moment. */
internal fun LedgerTransportProvider.recordingActivity(
    operations: LedgerUserOperations,
    record: MutableList<Boolean>,
) = LedgerTransportProvider {
    val inner = this@recordingActivity.open()
    object : LedgerTransport {
        override suspend fun exchange(apdu: ByteArray): ByteArray {
            record += operations.isActive
            return inner.exchange(apdu)
        }

        override fun close() = inner.close()
    }
}
