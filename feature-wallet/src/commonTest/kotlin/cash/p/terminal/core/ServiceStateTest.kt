package cash.p.terminal.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class ServiceStateTest {

    private class BlockingService : ServiceState<Int>() {
        var counter = 0

        // Mirrors ISendTransactionService: createState reads runBlocking-backed lazies.
        override fun createState(): Int = runBlocking { counter }

        fun update() {
            counter++
            emitState()
        }
    }

    @Test
    fun stateFlow_subscriberResumedWhileFirstStateIsBuilt_receivesLaterEmissions() {
        val service = BlockingService()
        val received = mutableListOf<Int>()
        val serviceReady = MutableStateFlow(false)
        // Unconfined shares the thread-local event loop that Main.immediate uses on the UI thread.
        val scope = CoroutineScope(Dispatchers.Unconfined)

        scope.launch {
            serviceReady.first { it }
            service.stateFlow.collect { received += it }
        }
        scope.launch {
            serviceReady.value = true
            service.update()
        }
        service.update()

        assertEquals(2, received.last())
        scope.cancel()
    }

    @Test
    fun emitState_whileInitialStateIsBuiltOnAnotherThread_isNotOverwrittenByInitialState() {
        val initialStateBuilding = CountDownLatch(1)
        val releaseInitialState = CountDownLatch(1)
        val service = object : ServiceState<Int>() {
            var counter = 0

            override fun createState(): Int {
                val state = counter
                if (initialStateBuilding.count > 0) {
                    initialStateBuilding.countDown()
                    releaseInitialState.await()
                }
                return state
            }

            fun update() {
                counter++
                emitState()
            }
        }

        val reader = thread { service.stateFlow }
        initialStateBuilding.await()
        val writer = thread { service.update() }
        while (writer.state != Thread.State.BLOCKED && writer.state != Thread.State.TERMINATED) Thread.yield()
        releaseInitialState.countDown()
        reader.join()
        writer.join()

        assertEquals(1, service.stateFlow.value)
    }
}
