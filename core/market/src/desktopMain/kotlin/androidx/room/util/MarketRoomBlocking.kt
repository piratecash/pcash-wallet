package androidx.room.util

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.ContinuationInterceptor

/**
 * Room generates calls to this for blocking DAO functions but ships it only in its Android artifact.
 * The package is dictated by that generated code, not chosen; the file name is unique so the facade
 * never clashes with the same bridge shipped by another kit on a shared classpath.
 */
fun <R> performBlocking(
    db: RoomDatabase,
    isReadOnly: Boolean,
    inTransaction: Boolean,
    block: (SQLiteConnection) -> R,
): R = runBlockingUninterruptible { performSuspending(db, isReadOnly, inTransaction, block) }

/**
 * Mirrors Room's Android-only `runBlockingUninterruptible`: the work is detached from the blocking
 * wait and started on this thread's event loop, so interrupting the caller cannot abandon a
 * transaction that is already running and later commits.
 */
@OptIn(DelicateCoroutinesApi::class)
internal fun <R> runBlockingUninterruptible(block: suspend CoroutineScope.() -> R): R {
    var interrupted = Thread.interrupted()
    try {
        return runBlocking {
            val eventLoop = requireNotNull(coroutineContext[ContinuationInterceptor])
            val result = CompletableDeferred<R>()
            GlobalScope.launch(eventLoop, CoroutineStart.UNDISPATCHED) {
                try {
                    result.complete(block())
                } catch (e: Throwable) {
                    result.completeExceptionally(e)
                }
            }
            while (!result.isCompleted) {
                try {
                    runBlocking(eventLoop) { result.await() }
                } catch (e: InterruptedException) {
                    interrupted = true
                }
            }
            result.await()
        }
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}
