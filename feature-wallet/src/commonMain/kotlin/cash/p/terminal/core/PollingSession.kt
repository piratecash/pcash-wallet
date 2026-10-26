package cash.p.terminal.core

import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import java.util.concurrent.atomic.AtomicInteger

inline fun AtomicInteger.onPollingStarted(action: () -> Unit) {
    incrementAndGet()
    try {
        action()
    } catch (throwable: Throwable) {
        decrementAndGet()
        throw throwable
    }
}

suspend inline fun AtomicInteger.onPollingStartedSuspend(
    crossinline action: suspend () -> Unit,
) {
    incrementAndGet()
    try {
        action()
    } catch (throwable: Throwable) {
        decrementAndGet()
        throw throwable
    }
}

inline fun AtomicInteger.onPollingStopped(
    backgroundManager: IBackgroundManager,
    action: () -> Unit,
) {
    try {
        if (backgroundManager.stateFlow.value != BackgroundManagerState.EnterForeground) {
            action()
        }
    } finally {
        decrementAndGet()
    }
}

suspend inline fun AtomicInteger.onPollingStoppedSuspend(
    backgroundManager: IBackgroundManager,
    crossinline action: suspend () -> Unit,
) {
    try {
        if (backgroundManager.stateFlow.value != BackgroundManagerState.EnterForeground) {
            action()
        }
    } finally {
        decrementAndGet()
    }
}
