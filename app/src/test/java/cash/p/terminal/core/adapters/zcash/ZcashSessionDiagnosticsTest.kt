package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import cash.p.terminal.core.managers.APP_LOG_DEDUP_WINDOW_MS
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.zcash.SyncStage
import cash.p.zcash.ZcashException
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class ZcashSessionDiagnosticsTest {
    private val logger = mockk<AppLogger>(relaxed = true)
    private val tracker = mockk<NetworkErrorTracker>(relaxed = true)
    private val databaseFiles = mockk<ZcashDatabaseFiles> { every { diagnostics(any()) } returns "db-fs: test" }
    private val time = TestTimeSource()
    private val diagnostics = ZcashSessionDiagnostics(logger, tracker, "account", SERVER_URL, databaseFiles, time)

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

    @Test
    fun syncFailed_databaseError_logsFilesystemDiagnosticsOnce() {
        repeat(2) { diagnostics.syncFailed(failure("error returned from database", SyncStage.SCAN)) }

        verify(exactly = 1) { logger.warning("db-fs: test") }
    }

    @Test
    fun syncFailed_networkError_doesNotLogFilesystemDiagnostics() {
        diagnostics.syncFailed(failure())

        verify(exactly = 0) { logger.warning(match { it.startsWith("db-fs:") }) }
    }

    @Test
    fun syncFailed_concurrentIdenticalFailures_writesAppLogOnce() {
        var reentered = false
        lateinit var racing: ZcashSessionDiagnostics
        // markNow() sits between the dedup check and the write, so re-entering here models a second caller.
        val reentrantTime = object : TimeSource {
            override fun markNow(): TimeMark {
                if (!reentered) {
                    reentered = true
                    racing.syncFailed(failure())
                }
                return time.markNow()
            }
        }
        racing = ZcashSessionDiagnostics(logger, tracker, "account", SERVER_URL, databaseFiles, reentrantTime)

        racing.syncFailed(failure())

        verifyWrites(1)
    }

    private companion object {
        const val SERVER_URL = "https://zec.rocks:443"
    }
}
