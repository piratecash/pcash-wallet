package cash.p.terminal.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/** A coroutine on a real thread can outlive a bare cancel() and call a mock the test removes next. */
fun CoroutineScope.cancelAndAwait() {
    val job = coroutineContext.job
    runBlocking { job.cancelAndJoin() }
}
