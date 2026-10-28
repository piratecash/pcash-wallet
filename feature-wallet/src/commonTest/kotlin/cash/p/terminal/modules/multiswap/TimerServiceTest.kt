package cash.p.terminal.modules.multiswap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerServiceTest {

    private val dispatcher = StandardTestDispatcher()
    private val uptimeProvider = VirtualUptimeProvider(dispatcher.scheduler)
    private val timerService = TimerService(uptimeProvider)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        timerService.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun start_resumptionDelayedPastDeadline_timesOut() = runTest(dispatcher) {
        timerService.start(10)
        runCurrent()

        uptimeProvider.sleptMillis = 20_000
        advanceTimeBy(1_001)

        assertTrue(timerService.stateFlow.value.timeout)
    }

    @Test
    fun start_firstDispatchDelayedPastDeadline_timesOutWithoutTick() = runTest(dispatcher) {
        val states = mutableListOf<TimerService.State>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            timerService.stateFlow.collect { states += it }
        }
        timerService.start(10)
        val publishedBeforeDispatch = states.size

        uptimeProvider.sleptMillis = 20_000
        runCurrent()

        assertEquals(
            listOf(TimerService.State(remaining = 10, timeout = true)),
            states.drop(publishedBeforeDispatch),
        )
    }

    @Test
    fun start_clockMovedBackward_timesOut() = runTest(dispatcher) {
        timerService.start(10)
        runCurrent()

        // Desktop uptime is wall time, so a clock correction can move it backwards.
        uptimeProvider.sleptMillis = -60_000
        advanceTimeBy(1_001)

        assertTrue(timerService.stateFlow.value.timeout)
    }

    @Test
    fun start_tickDelivered_countsDownFromDeadline() = runTest(dispatcher) {
        timerService.start(10)
        runCurrent()

        uptimeProvider.sleptMillis = 3_000
        advanceTimeBy(1_001)

        assertEquals(TimerService.State(remaining = 6, timeout = false), timerService.stateFlow.value)
    }
}
