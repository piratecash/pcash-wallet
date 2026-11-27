package cash.p.terminal.modules.transactions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCalendarControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val host = FakeHost()

    private val toDate = TransactionPeriod(from = null, to = LocalDate.of(2026, 11, 12))
    private val range = TransactionPeriod(from = LocalDate.of(2026, 8, 1), to = LocalDate.of(2026, 8, 28))

    @Test
    fun apply_rangeFromOpenSheet_hostReceivesPeriodAndSheetCloses() = runTest(dispatcher) {
        val controller = controller()
        controller.onCalendarClick()

        controller.apply(range)

        assertEquals(listOf<TransactionPeriod?>(range), host.applied)
        assertEquals(range, controller.uiState(historyExhausted = false).period)
        assertFalse(controller.uiState(historyExhausted = false).sheetVisible)
    }

    @Test
    fun apply_samePeriodAgain_appliesOnce() = runTest(dispatcher) {
        val controller = controller()

        controller.onCalendarClick()
        controller.apply(range)
        controller.onCalendarClick()
        controller.apply(range)

        assertEquals(listOf<TransactionPeriod?>(range), host.applied)
    }

    @Test
    fun clear_appliedPeriod_hostReceivesNull() = runTest(dispatcher) {
        val controller = controller()
        controller.onCalendarClick()
        controller.apply(range)

        controller.clear()

        assertEquals(listOf(range, null), host.applied)
        assertFalse(controller.uiState(historyExhausted = false).active)
    }

    @Test
    fun onLeave_openSheetWithPeriod_clearsPeriodAndClosesSheet() = runTest(dispatcher) {
        val controller = controller()
        controller.onCalendarClick()
        controller.apply(range)
        controller.onCalendarClick()

        controller.onLeave()

        val state = controller.uiState(historyExhausted = false)
        assertNull(state.period)
        assertFalse(state.sheetVisible)
        assertEquals(listOf(range, null), host.applied)
    }

    @Test
    fun apply_toDatePeriod_requestsOldestAndExposesItsDay() = runTest(dispatcher) {
        val oldest = LocalDate.of(2021, 3, 3)
        host.oldest = oldest.atStartOfDay(ZoneId.systemDefault()).plusHours(15).toEpochSecond()
        val controller = controller()

        controller.onCalendarClick()
        controller.apply(toDate)

        assertEquals(1, host.oldestRequests)
        assertEquals(oldest, controller.uiState(historyExhausted = false).oldestDate)
    }

    @Test
    fun apply_rangePeriod_doesNotRequestOldest() = runTest(dispatcher) {
        val controller = controller()

        controller.onCalendarClick()
        controller.apply(range)
        controller.onSelectionChanged()

        assertEquals(0, host.oldestRequests)
        assertNull(controller.uiState(historyExhausted = false).oldestDate)
    }

    @Test
    fun onSelectionChanged_toDatePeriod_requestsOldestAgain() = runTest(dispatcher) {
        host.oldest = 1_600_000_000
        val controller = controller()
        controller.onCalendarClick()
        controller.apply(toDate)

        controller.onSelectionChanged()

        assertEquals(2, host.oldestRequests)
    }

    @Test
    fun uiState_oldestAfterChosenDay_hidesOldest() = runTest(dispatcher) {
        host.oldest = toDate.to.plusDays(3).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val controller = controller()

        controller.onCalendarClick()
        controller.apply(toDate)

        assertNull(controller.uiState(historyExhausted = false).oldestDate)
    }

    @Test
    fun uiState_exhaustedHistory_showsEndFooterOnlyForRange() = runTest(dispatcher) {
        val controller = controller()

        controller.onCalendarClick()
        controller.apply(toDate)
        assertFalse(controller.uiState(historyExhausted = true).showsEndFooter)

        controller.onCalendarClick()
        controller.apply(range)
        assertTrue(controller.uiState(historyExhausted = true).showsEndFooter)
        assertFalse(controller.uiState(historyExhausted = false).showsEndFooter)
    }

    @Test
    fun apply_sheetClosedByCleanup_ignoresLateApply() = runTest(dispatcher) {
        val controller = controller()
        controller.onCalendarClick()
        controller.onLeave()

        controller.apply(range)

        assertNull(controller.uiState(historyExhausted = false).period)
        assertEquals(listOf<TransactionPeriod?>(), host.applied)
    }

    @Test
    fun onSelectionChanged_oldestLookupPending_hidesPreviousSelectionOldest() = runTest(dispatcher) {
        host.oldest = LocalDate.of(2020, 1, 1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val controller = controller()
        controller.onCalendarClick()
        controller.apply(toDate)
        val pending = CompletableDeferred<Long?>()
        host.pendingOldest = pending

        controller.onSelectionChanged()

        assertNull(controller.uiState(historyExhausted = false).oldestDate)
        pending.complete(LocalDate.of(2024, 5, 5).atStartOfDay(ZoneId.systemDefault()).toEpochSecond())
        assertEquals(LocalDate.of(2024, 5, 5), controller.uiState(historyExhausted = false).oldestDate)
    }

    private fun TestScope.controller() = TransactionCalendarController(this, host)

    private class FakeHost : TransactionCalendarController.Host {
        val applied = mutableListOf<TransactionPeriod?>()
        var oldest: Long? = null
        var oldestRequests = 0
        var pendingOldest: CompletableDeferred<Long?>? = null

        override fun onCalendarStateChanged() = Unit

        override suspend fun applyPeriod(period: TransactionPeriod?) {
            applied += period
        }

        override suspend fun oldestTimestamp(): Long? {
            oldestRequests++
            return pendingOldest?.await() ?: oldest
        }
    }
}
