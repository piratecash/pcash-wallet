package cash.p.terminal.shared.desktop.lock

import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import io.horizontalsystems.core.AppForegroundState
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ICurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.util.concurrent.atomic.AtomicLong

/** Locks the unlocked app after the configured idle time; "Immediate" means leaving the window. */
class DesktopInactivityLock(
    private val vault: DesktopVault,
    private val settingsStore: DesktopSettingsStore,
    private val foregroundState: AppForegroundState,
    private val currentDateProvider: ICurrentDateProvider,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val lastActivityAt = AtomicLong()

    @Volatile
    private var idleLimitMs = Long.MAX_VALUE

    fun onUserActivity() {
        val now = currentDateProvider.currentDate.time
        // Input right after waking must not hide an idle period the sleep already completed.
        lastActivityAt.updateAndGet { last -> if (now - last >= idleLimitMs) last else now }
    }

    /** Every input of the app counts, dialogs, sheets and menus included: they live outside the main content. */
    fun watchUserInput(): AWTEventListener = AWTEventListener { onUserActivity() }
        .also { Toolkit.getDefaultToolkit().addAWTEventListener(it, USER_INPUT_EVENTS) }

    fun start(scope: CoroutineScope): Job = scope.launch(dispatcherProvider.io) {
        vault.state.collectLatest { state ->
            if (state !is DesktopVaultState.Unlocked) return@collectLatest
            // A new interval restarts the wait: the chosen timeout must hold without user activity.
            settingsStore.autoLockIntervalFlow.collectLatest { interval ->
                awaitLockCondition(interval)
                vault.lock()
            }
        }
    }

    private suspend fun awaitLockCondition(interval: AutoLockInterval) {
        if (interval == AutoLockInterval.IMMEDIATE) {
            foregroundState.inForeground.first { !it }
            return
        }
        lastActivityAt.set(currentDateProvider.currentDate.time)
        idleLimitMs = interval.intervalInSeconds * 1_000L
        try {
            currentDateProvider.awaitWallTime { lastActivityAt.get() + idleLimitMs }
        } finally {
            idleLimitMs = Long.MAX_VALUE
        }
    }
}

/** Coroutine delays stop while the computer sleeps; a deadline in wall time must not. */
internal suspend fun ICurrentDateProvider.awaitWallTime(deadline: () -> Long) {
    while (true) {
        val remaining = deadline() - currentDate.time
        if (remaining <= 0) return
        delay(minOf(remaining, WALL_CLOCK_CHECK_MS))
    }
}

private const val WALL_CLOCK_CHECK_MS = 1_000L

private const val USER_INPUT_EVENTS = AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or
    AWTEvent.MOUSE_WHEEL_EVENT_MASK or AWTEvent.KEY_EVENT_MASK
