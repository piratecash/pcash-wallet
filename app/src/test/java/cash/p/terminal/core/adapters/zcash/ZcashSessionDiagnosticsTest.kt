package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.managers.APP_LOG_DEDUP_WINDOW_MS
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.zcash.SyncStage
import cash.p.zcash.ZcashException
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

@OptIn(ExperimentalTime::class)
class ZcashSessionDiagnosticsTest {
    private val logger = mockk<AppLogger>(relaxed = true)
    private val tracker = mockk<NetworkErrorTracker>(relaxed = true)
    private val time = TestTimeSource()
    private val diagnostics = ZcashSessionDiagnostics(logger, tracker, "account", SERVER_URL, time)

    private fun failure(message: String = "no route", stage: SyncStage = SyncStage.TARGET) =
        ZcashException(message, stage = stage)

    private fun verifyWrites(count: Int) =
        verify(exactly = count) { logger.warning(match { it.startsWith("sync failed") }) }

    @Test
    fun syncFailed_sameFailureRepeatedWithinWindow_writesAppLogOnce() {
        repeat(3) { diagnostics.syncFailed(failure()) }

        verifyWrites(1)
        verify(exactly = 3) { tracker.record(BlockchainType.Zcash, "account", any()) }
    }

    @Test
    fun syncFailed_sameFailureAfterWindow_writesAgain() {
        diagnostics.syncFailed(failure())
        time += (APP_LOG_DEDUP_WINDOW_MS + 1).milliseconds
        diagnostics.syncFailed(failure())

        verifyWrites(2)
    }

    @Test
    fun syncFailed_differentFailureWithinWindow_writesEach() {
        diagnostics.syncFailed(failure())
        diagnostics.syncFailed(failure(stage = SyncStage.SCAN))
        diagnostics.syncFailed(failure(message = "other"))

        verifyWrites(3)
    }

    private companion object {
        const val SERVER_URL = "https://zec.rocks:443"
    }
}
