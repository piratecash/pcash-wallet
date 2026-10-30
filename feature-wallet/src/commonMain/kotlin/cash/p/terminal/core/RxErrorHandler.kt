package cash.p.terminal.core

import co.touchlab.kermit.Logger
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException

private val logger = Logger.withTag("RxErrorHandler")

/** Takes errors no subscriber is left to receive; a network call interrupted by disposal is expected. */
fun installRxErrorHandler(nonFatalReporter: NonFatalReporter) {
    RxJavaPlugins.setErrorHandler { error ->
        if (error.isInterruptedNetworkCall()) {
            logger.d { "Disposed network call: ${error.cause}" }
        } else {
            nonFatalReporter.report(error, "Undeliverable RxJava error")
        }
    }
}

// RxJava passes bugs (OnErrorNotImplemented, Composite, ...) unwrapped, so only Undeliverable can be a disposal leftover.
private fun Throwable.isInterruptedNetworkCall(): Boolean =
    this is UndeliverableException &&
        generateSequence(cause, Throwable::cause).any { it is IOException || it is InterruptedException }
