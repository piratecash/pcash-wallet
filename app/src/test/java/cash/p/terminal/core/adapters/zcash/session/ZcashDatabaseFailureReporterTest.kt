package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.NonFatalReporter
import cash.p.zcash.DatabasePool
import cash.p.zcash.DatabaseStats
import cash.p.zcash.ZcashSdk
import io.horizontalsystems.core.logger.AppLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        lastSeq: Long = 0,
        pools: List<DatabasePool> = emptyList(),
    ) = DatabaseStats(
        opens = 5,
        nativeCantOpen = native,
        cantOpenFailures = acquire,
        recoveredByRetry = recovered,
        lastError = null,
        recent = recent,
        pools = pools,
        lastSeq = lastSeq,
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

        report(stats(native = 2, acquire = 1, recovered = 1, recent = recent, lastSeq = recent.size.toLong()))

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

    @Test
    fun checkCantOpen_secondDeltaWithTwoNewEntries_printsOnlyThoseTwo() {
        val first = listOf("14: a", "14: b", "14: c")
        report(stats(native = 1, recent = first, lastSeq = 3))
        val second = first + listOf("14: d", "14: e")

        report(stats(native = 2, recent = second, lastSeq = 5))

        verify(exactly = 1) { logger.warning("14: a") }
        verify(exactly = 1) { logger.warning("14: d") }
        verify(exactly = 1) { logger.warning("14: e") }
        verify(exactly = 0) { logger.warning(match { it.contains("evicted unseen") }) }
    }

    @Test
    fun checkCantOpen_gapLargerThanRing_logsEvictedCountBeforeEntries() {
        val ring = listOf("14: x", "14: y")

        report(stats(native = 1, recent = ring, lastSeq = 7))

        verifyOrder {
            logger.warning("sqlite log: 5 entries evicted unseen")
            logger.warning("14: x")
            logger.warning("14: y")
        }
    }

    @Test
    fun checkCantOpen_counterGrewWithoutNewEntries_keepsSeenSequence() {
        report(stats(native = 1, recent = listOf("14: a"), lastSeq = 1))
        report(stats(native = 2, recent = listOf("14: a"), lastSeq = 1))

        verify(exactly = 1) { logger.warning("14: a") }
    }

    @Test
    fun databaseFailure_poolsPresent_logsMappedAndForeignPools() {
        every { databaseFiles.accountIdOf("/db/wallet_account.sqlite3") } returns ACCOUNT_ID
        every { databaseFiles.accountIdOf("/elsewhere/x.sqlite3") } returns null
        every { ZcashSdk.databaseStats() } returns stats(
            pools = listOf(
                DatabasePool(id = 1, path = "/db/wallet_account.sqlite3", size = 4, idle = 3),
                DatabasePool(id = 2, path = "/elsewhere/x.sqlite3", size = 1, idle = 0),
            ),
        )

        reporter.databaseFailure(ACCOUNT_ID, logger)

        verify(exactly = 1) {
            logger.warning("db-pools: account pool=1 size=4 idle=3; other pool=2 size=1 idle=0")
        }
    }

    @Test
    fun databaseFailure_statsUnavailable_writesOnlyDbFsLine() {
        every { ZcashSdk.databaseStats() } throws UnsatisfiedLinkError()

        reporter.databaseFailure(ACCOUNT_ID, logger)

        verify(exactly = 1) { logger.warning("db-fs: test") }
        verify(exactly = 0) { logger.warning(match { it.startsWith("db-pools:") }) }
    }

    @Test
    fun nativeLibraryNames_mixedMaps_returnsAppLibraryNamesDistinctSorted() {
        val maps = sequenceOf(
            "7000 r-xp 00000000 fd:01 1 /data/app/~~x/cash.p.terminal-y/lib/arm64/libzcash.so",
            "8000 r--p 00001000 fd:01 1 /data/app/~~x/cash.p.terminal-y/lib/arm64/libzcash.so",
            "9000 r-xp 00000000 fd:01 2 /data/app/~~x/cash.p.terminal-y/lib/arm64/liba.so",
            "a000 r-xp 00000000 fd:01 3 /system/lib64/libc.so",
            "b000 r--s 00000000 fd:01 4 /data/app/~~x/cash.p.terminal-y/base.apk",
            "c000 rw-p 00000000 00:00 0",
        )

        assertEquals(listOf("liba.so", "libzcash.so"), nativeLibraryNames(maps))
    }

    @Test
    fun processStarted_calledTwice_logsOneLine() {
        reporter.processStarted(logger)
        reporter.processStarted(logger)

        verify(exactly = 1) { logger.info(match { it.startsWith("process: start pid=") && it.contains(" libs=") }) }
    }

    @Test
    fun checkCantOpen_twoDeltas_logsFirstCantopenOnce() {
        val lines = mutableListOf<String>()
        every { logger.warning(capture(lines)) } returns Unit

        report(stats(native = 1))
        report(stats(native = 2))

        assertEquals(1, lines.count { it.startsWith("process: first-cantopen pid=") })
        val summaryAt = lines.indexOfFirst { it.startsWith("sqlite cantopen") }
        assertTrue(lines.indexOfFirst { it.startsWith("process:") } > summaryAt)
    }

    private companion object {
        const val ACCOUNT_ID = "account"
    }
}
