package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.core.TestDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalQueueTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = JournalQueue(TestDispatcherProvider(Dispatchers.IO, scope))

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun submit_manyJobsOnAParallelDispatcher_runInSubmissionOrder() = runBlocking {
        val ran = Collections.synchronizedList(mutableListOf<Int>())

        repeat(JOBS) { index -> queue.submit("job $index") { ran.add(index) } }
        checkNotNull(scope.coroutineContext[Job]).children.toList().joinAll()

        assertEquals(List(JOBS) { it }, ran.toList())
    }

    @Test
    fun submit_failingJob_laterJobsStillRun() = runBlocking {
        val ran = Collections.synchronizedList(mutableListOf<Int>())

        queue.submit("failing") { error("test") }
        queue.submit("next") { ran.add(1) }
        checkNotNull(scope.coroutineContext[Job]).children.toList().joinAll()

        assertEquals(listOf(1), ran.toList())
    }

    private companion object {
        const val JOBS = 2_000
    }
}
