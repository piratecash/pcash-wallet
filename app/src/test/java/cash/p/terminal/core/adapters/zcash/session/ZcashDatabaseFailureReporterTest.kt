package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.NonFatalReporter
import cash.p.zcash.DatabaseStats
import cash.p.zcash.ZcashSdk
import io.horizontalsystems.core.logger.AppLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

@OptIn(ExperimentalTime::class)
class ZcashDatabaseFailureReporterTest {
    private val databaseFiles = mockk<ZcashDatabaseFiles> { every { diagnostics(ACCOUNT_ID) } returns "db-fs: test" }
    private val nonFatalReporter = mockk<NonFatalReporter>(relaxed = true)
    private val logger = mockk<AppLogger>(relaxed = true)
    private val time = TestTimeSource()
    private val reporter = ZcashDatabaseFailureReporter(databaseFiles, nonFatalReporter, time)

    @Before
    fun setUp() {
        mockkObject(ZcashSdk)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun stats(
        native: Long = 0,
        acquire: Long = 0,
        recovered: Long = 0,
        recent: List<String> = emptyList(),
    ) = DatabaseStats(
        opens = 5,
        nativeCantOpen = native,
        cantOpenFailures = acquire,
        recoveredByRetry = recovered,
        lastError = null,
        recent = recent,
    )

    private fun report(stats: DatabaseStats) {
        every { ZcashSdk.databaseStats() } returns stats
        reporter.checkCantOpen(logger)
    }

    private fun osLine(syscall: String, line: Int = 44821) =
        "14: os_unix.c:$line: (2) $syscall(*.sqlite3) - No such file or directory"

    @Test
    fun databaseFailure_anyDatabaseFailure_writesDbFsLine() {
        reporter.databaseFailure(ACCOUNT_ID, logger)

        verify(exactly = 1) { logger.warning("db-fs: test") }
    }

    @Test
    fun checkCantOpen_counterGrewWithRecovery_logsDeltaWithRecentAndRecordsNonFatal() {
        val recent = listOf(
            osLine("open"),
            "14: acquire attempt 1/4 failed: x",
            "14: acquire recovered after 1 failed attempts"
        )

        report(stats(native = 2, acquire = 1, recovered = 1, recent = recent))

        verify(exactly = 1) { logger.warning("sqlite cantopen native +2 acquire +1 recovered +1 opens=5") }
        recent.forEach { verify(exactly = 1) { logger.warning(it) } }
        val message = slot<String>()
        verify(exactly = 1) { nonFatalReporter.report(any<ZcashCantOpenException>(), capture(message)) }
        assertEquals("sqlite cantopen native +2 acquire +1 recovered +1 opens=5", message.captured)
    }

    @Test
    fun checkCantOpen_counterUnchanged_writesNothing() {
        report(stats(native = 1, acquire = 1))
        report(stats(native = 1, acquire = 1))

        verify(exactly = 1) { logger.warning(match { it.startsWith("sqlite cantopen") }) }
        verify(exactly = 1) { nonFatalReporter.report(any(), any()) }
    }

    @Test
    fun checkCantOpen_onlyAcquireCounterGrew_logsAndRecords() {
        report(stats(acquire = 3))

        verify(exactly = 1) { logger.warning("sqlite cantopen native +0 acquire +3 recovered +0 opens=5") }
        verify(exactly = 1) { nonFatalReporter.report(any(), any()) }
    }

    @Test
    fun checkCantOpen_recoveryAfterFailureAlreadyLogged_logsRecovery() {
        report(stats(acquire = 1))

        report(stats(acquire = 1, recovered = 1))

        verify(exactly = 1) { logger.warning("sqlite cantopen native +0 acquire +0 recovered +1 opens=5") }
    }

    @Test
    fun checkCantOpen_sameSignatureTwiceWithinSixHours_logsBothRecordsNonFatalOnce() {
        report(stats(native = 1, recent = listOf(osLine("open"))))
        time += 1.hours
        report(stats(native = 2, recent = listOf(osLine("open", line = 44822))))

        verify(exactly = 2) { logger.warning(match { it.startsWith("sqlite cantopen") }) }
        verify(exactly = 1) { nonFatalReporter.report(any(), any()) }
    }

    @Test
    fun checkCantOpen_differentSignatureWithinSixHours_recordsNonFatalForEach() {
        report(stats(native = 1, recent = listOf(osLine("open"))))
        report(stats(native = 2, recent = listOf(osLine("lstat"))))

        verify(exactly = 2) { nonFatalReporter.report(any(), any()) }
    }

    @Test
    fun checkCantOpen_sameSignatureAfterSixHours_recordsNonFatalAgain() {
        report(stats(native = 1, recent = listOf(osLine("open"))))
        time += 6.hours
        report(stats(native = 2, recent = listOf(osLine("open"))))

        verify(exactly = 2) { nonFatalReporter.report(any(), any()) }
    }

    @Test
    fun checkCantOpen_noOsLayerEntry_usesUnknownSignatureOnce() {
        val breakpoint = "14: cannot open file at line 187715 of [abc]"
        report(stats(native = 1, recent = listOf(breakpoint)))
        report(stats(native = 2, recent = listOf(breakpoint, "14: acquire attempt 1/4 failed: x")))

        verify(exactly = 1) { nonFatalReporter.report(any(), any()) }
    }

    @Test
    fun checkCantOpen_statsUnavailable_writesNothing() {
        every { ZcashSdk.databaseStats() } throws UnsatisfiedLinkError()

        reporter.checkCantOpen(logger)

        verify(exactly = 0) { logger.warning(any<String>()) }
        verify(exactly = 0) { nonFatalReporter.report(any(), any()) }
    }

    private companion object {
        const val ACCOUNT_ID = "account"
    }
}
