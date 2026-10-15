package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.modules.pin.core.LockoutManager
import cash.p.terminal.modules.pin.core.LockoutState
import cash.p.terminal.modules.pin.core.LockoutUntilDateFactory
import io.horizontalsystems.core.CurrentDateProvider
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.util.Date
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val THIRTY_MINUTES_MS = 30 * 60 * 1000L

/** The counter lives in the open settings file, so a forged value must not extend the lockout. */
class DesktopLockoutStorageTest {

    private val directory = createTempDirectory("desktop-lockout").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = DesktopSettingsStore(File(directory, "settings.preferences_pb"), scope)
    private val uptimeProvider = DesktopUptimeProvider()
    private val lockoutManager = LockoutManager(store, uptimeProvider, LockoutUntilDateFactory(CurrentDateProvider()))

    @AfterTest
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun currentState_lockoutUptimeForgedIntoTheFuture_isNotLocked() {
        store.failedAttempts = 8
        store.lockoutUptime = uptimeProvider.uptime + 10 * 365 * 24 * 60 * 60 * 1000L

        assertIs<LockoutState.Unlocked>(lockoutManager.currentState)
    }

    @Test
    fun currentState_maxFailedAttempts_locksForAtMostThirtyMinutes() {
        store.failedAttempts = 20
        store.lockoutUptime = uptimeProvider.uptime

        val locked = assertIs<LockoutState.Locked>(lockoutManager.currentState)

        assertTrue(locked.until.time - Date().time <= THIRTY_MINUTES_MS)
    }

    @Test
    fun dropFailedAttempts_afterFailures_clearsTheStoredCounter() {
        lockoutManager.didFailUnlock()

        lockoutManager.dropFailedAttempts()

        assertEquals(null, store.failedAttempts)
    }
}
