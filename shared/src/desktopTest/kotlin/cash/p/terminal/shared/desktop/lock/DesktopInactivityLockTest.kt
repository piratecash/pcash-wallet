package cash.p.terminal.shared.desktop.lock

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import io.horizontalsystems.core.AppForegroundState
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.ICurrentDateProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.util.Date
import javax.swing.JPanel
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopInactivityLockTest {

    private val vaultState = MutableStateFlow<DesktopVaultState>(
        DesktopVaultState.Unlocked(mockk<DesktopStorageSession>(relaxed = true))
    )
    private val vault = mockk<DesktopVault> {
        every { state } returns vaultState
        coEvery { lock() } returns Unit
    }
    private val foreground = MutableStateFlow(true)
    private val foregroundState = object : AppForegroundState {
        override val inForeground = foreground
    }
    private val interval = MutableStateFlow(AutoLockInterval.AFTER_1_MIN)
    private val settingsStore = mockk<DesktopSettingsStore> {
        every { autoLockIntervalFlow } returns interval
    }
    // Wall time runs on while the computer sleeps; the coroutine clock does not.
    private var sleptMillis = 0L

    @Test
    fun start_idleForTheWholeInterval_locksTheVault() = runTest {
        startLock()

        advanceTimeBy(59_000)
        runCurrent()
        coVerify(exactly = 0) { vault.lock() }

        advanceTimeBy(2_000)
        runCurrent()
        coVerify(exactly = 1) { vault.lock() }
    }

    @Test
    fun onUserActivity_beforeTheIntervalEnds_restartsTheCountdown() = runTest {
        val lock = startLock()

        advanceTimeBy(50_000)
        lock.onUserActivity()
        advanceTimeBy(50_000)
        runCurrent()
        coVerify(exactly = 0) { vault.lock() }

        advanceTimeBy(20_000)
        runCurrent()
        coVerify(exactly = 1) { vault.lock() }
    }

    @Test
    fun watchUserInput_clickOutsideTheMainContent_restartsTheCountdown() = runTest {
        val lock = startLock()
        val listener = lock.watchUserInput()
        try {
            advanceTimeBy(50_000)
            // A dialog, sheet or menu is a component of its own, outside the main content.
            val popup = JPanel()
            popup.dispatchEvent(MouseEvent(popup, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false))
            advanceTimeBy(50_000)
            runCurrent()

            coVerify(exactly = 0) { vault.lock() }
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }

    @Test
    fun onUserActivity_afterSleepingLongerThanTheInterval_locksTheVault() = runTest {
        val lock = startLock()
        advanceTimeBy(30_000)
        runCurrent()

        sleptMillis = 2 * 60 * 60_000L
        lock.onUserActivity()
        advanceTimeBy(1_001)
        runCurrent()

        coVerify(exactly = 1) { vault.lock() }
    }

    @Test
    fun start_sleptLongerThanTheIntervalWithoutInputAfterWaking_locksTheVault() = runTest {
        interval.value = AutoLockInterval.AFTER_1_HOUR
        startLock()
        advanceTimeBy(30_000)
        runCurrent()

        sleptMillis = 2 * 60 * 60_000L
        advanceTimeBy(1_001)
        runCurrent()

        coVerify(exactly = 1) { vault.lock() }
    }

    @Test
    fun start_immediateInterval_locksOnLeavingTheForeground() = runTest {
        interval.value = AutoLockInterval.IMMEDIATE
        startLock()

        advanceTimeBy(60 * 60_000)
        runCurrent()
        coVerify(exactly = 0) { vault.lock() }

        foreground.value = false
        runCurrent()
        coVerify(exactly = 1) { vault.lock() }
    }

    @Test
    fun start_intervalChangedFromImmediateToTimed_locksAfterTheNewInterval() = runTest {
        interval.value = AutoLockInterval.IMMEDIATE
        startLock()
        advanceTimeBy(10_000)
        runCurrent()

        interval.value = AutoLockInterval.AFTER_1_MIN
        advanceTimeBy(61_000)
        runCurrent()

        coVerify(exactly = 1) { vault.lock() }
    }

    @Test
    fun start_intervalChangedToImmediate_locksOnLeavingTheForeground() = runTest {
        startLock()
        advanceTimeBy(10_000)
        runCurrent()

        interval.value = AutoLockInterval.IMMEDIATE
        runCurrent()
        foreground.value = false
        runCurrent()

        coVerify(exactly = 1) { vault.lock() }
    }

    @Test
    fun start_vaultNotUnlocked_neverLocks() = runTest {
        vaultState.value = DesktopVaultState.Locked
        startLock()

        advanceTimeBy(60 * 60_000)
        foreground.value = false
        runCurrent()

        coVerify(exactly = 0) { vault.lock() }
    }

    private fun TestScope.startLock(): DesktopInactivityLock {
        val dispatcherProvider = TestDispatcherProvider(
            dispatcher = StandardTestDispatcher(testScheduler),
            applicationScope = backgroundScope,
        )
        val currentDateProvider = object : ICurrentDateProvider {
            override val currentDate get() = Date(testScheduler.currentTime + sleptMillis)
        }
        return DesktopInactivityLock(vault, settingsStore, foregroundState, currentDateProvider, dispatcherProvider)
            .also { it.start(backgroundScope) }
    }
}
