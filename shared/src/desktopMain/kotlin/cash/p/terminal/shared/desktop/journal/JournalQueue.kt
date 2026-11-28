package cash.p.terminal.shared.desktop.journal

import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** Journal work runs off the unlock path, one job at a time, in the order the unlocks happened. */
class JournalQueue(private val dispatcherProvider: DispatcherProvider) {
    private val logger = Logger.withTag("LoginJournal")
    private val tail = AtomicReference<Job?>(null)

    /** A journal that fails must never break the unlock that triggered it. */
    fun submit(name: String, work: suspend () -> Unit) {
        // Each job waits for the one submitted before it: the dispatcher may start them in any order.
        val previous = CompletableDeferred<Job?>()
        val job = dispatcherProvider.applicationScope.launch(dispatcherProvider.io) {
            previous.await()?.join()
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "$name failed" }
            }
        }
        previous.complete(tail.getAndSet(job))
    }
}
