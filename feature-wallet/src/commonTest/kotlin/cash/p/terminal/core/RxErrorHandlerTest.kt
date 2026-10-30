package cash.p.terminal.core

import io.mockk.mockk
import io.mockk.verify
import io.reactivex.exceptions.CompositeException
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.plugins.RxJavaPlugins
import org.junit.After
import org.junit.Test
import java.io.InterruptedIOException

class RxErrorHandlerTest {

    private val nonFatalReporter = mockk<NonFatalReporter>(relaxed = true)

    @After
    fun tearDown() {
        RxJavaPlugins.reset()
    }

    @Test
    fun installRxErrorHandler_disposedNetworkCall_notReported() {
        installRxErrorHandler(nonFatalReporter)

        RxJavaPlugins.onError(RuntimeException(InterruptedIOException("interrupted")))

        verify(exactly = 0) { nonFatalReporter.report(any(), any()) }
    }

    @Test
    fun installRxErrorHandler_otherError_reported() {
        installRxErrorHandler(nonFatalReporter)
        val error = IllegalStateException("boom")

        RxJavaPlugins.onError(error)

        verify { nonFatalReporter.report(error, any()) }
    }

    @Test
    fun installRxErrorHandler_missingErrorHandlerForNetworkError_reported() {
        installRxErrorHandler(nonFatalReporter)
        val error = OnErrorNotImplementedException(InterruptedIOException("interrupted"))

        RxJavaPlugins.onError(error)

        verify { nonFatalReporter.report(error, any()) }
    }

    @Test
    fun installRxErrorHandler_failingErrorCallbackAfterNetworkError_reported() {
        installRxErrorHandler(nonFatalReporter)
        val error = CompositeException(InterruptedIOException("interrupted"), IllegalStateException("callback"))

        RxJavaPlugins.onError(error)

        verify { nonFatalReporter.report(error, any()) }
    }
}
