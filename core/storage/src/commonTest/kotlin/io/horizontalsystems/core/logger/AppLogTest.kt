package io.horizontalsystems.core.logger

import io.horizontalsystems.core.storage.GroupedLogRow
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AppLogTest {

    private val logsDao = mockk<LogsDao>(relaxed = true)

    @Before
    fun setUp() {
        startKoin { modules(module { single { logsDao } }) }
    }

    /** AppLog is a singleton with one shared executor: finish pending tasks before the next test restubs. */
    @After
    fun tearDown() {
        runBlocking { AppLog.writeLog(StringBuilder()) }
        stopKoin()
    }

    @Test
    fun info_preservesLiteralMessageAndTag() {
        val entry = captureNextEntry()

        AppLog.info("literal/tag", "literal %s \$value")

        assertTrue(entry.inserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(AppLog.LEVEL_INFO, entry.value.level)
        assertEquals("literal/tag", entry.value.actionId)
        assertEquals("literal %s \$value", entry.value.message)
    }

    @Test
    fun warning_preservesLiteralMessageAndTag() {
        val entry = captureNextEntry()

        AppLog.warning("warning/tag", "literal %s \$value")

        assertTrue(entry.inserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(AppLog.LEVEL_WARN, entry.value.level)
        assertEquals("warning/tag", entry.value.actionId)
        assertEquals("literal %s \$value", entry.value.message)
    }

    @Test
    fun warning_exceptionWithManyFrames_retainsFiveFrames() {
        val entry = captureNextEntry()
        val error = IllegalStateException("boom").apply {
            stackTrace = (0..6).map { index ->
                StackTraceElement("Class$index", "method$index", "File$index.kt", index + 1)
            }.toTypedArray()
        }
        AppLog.warning("warning/tag", "warning", error)

        assertTrue(entry.inserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(AppLog.LEVEL_WARN, entry.value.level)
        assertEquals("warning/tag", entry.value.actionId)
        assertTrue(entry.value.message.startsWith("warning: java.lang.IllegalStateException: boom"))
        (0..4).forEach { index ->
            assertTrue(entry.value.message.contains("Class$index.method$index(File$index.kt:${index + 1})"))
        }
        assertFalse(entry.value.message.contains("Class5.method5"))
    }

    @Test
    fun readMethods_useExpectedSelectionAndOrderAndPreserveMultilineMessages() = runTest {
        val first = logEntry(1, "first\ncontinued")
        val second = logEntry(2, "second")
        coEvery { logsDao.getRecent(500) } returns listOf(second, first)
        every { logsDao.getRecentByTag("tag", 300) } returns listOf(second, first)

        val taggedLogs = AppLog.getRecentLog("tag")
        assertEquals(listOf("1", "2"), AppLog.getLog().messages().keys.toList())
        assertEquals(listOf("1", "2"), taggedLogs.messages().keys.toList())
        assertEquals(
            "1970-01-01 00:00:00.000 first\ncontinued",
            taggedLogs.messages().getValue("1"),
        )
    }

    @Test
    fun info_insertedEntry_isFollowedByTrimToNewest() {
        val dao = recordingDao(expectedCalls = 2)

        AppLog.info("tag", "message")

        assertEquals(listOf("insert", "trim:$MAX_ENTRIES"), dao.awaitCalls())
    }

    @Test
    fun warning_insertedEntry_isFollowedByTrimToNewest() {
        val dao = recordingDao(expectedCalls = 2)

        AppLog.warning("tag", "message", IllegalStateException("boom"))

        assertEquals(listOf("insert", "trim:$MAX_ENTRIES"), dao.awaitCalls())
    }

    @Test
    fun cleanupOldLogs_always_deletesOldRowsAndTrimsToNewest() {
        val dao = recordingDao(expectedCalls = 2)

        AppLog.cleanupOldLogs()

        assertEquals(listOf("deleteOlderThan", "trim:$MAX_ENTRIES"), dao.awaitCalls())
    }

    @Test
    fun writeLog_groupedRows_writesOneHeaderPerActionIdAndOneLinePerEntry() {
        val rows = groupedRows(
            logEntry(1, "one", "a"),
            logEntry(2, "two", "a"),
            logEntry(3, "three", "b"),
        )
        every { logsDao.getGroupedPage(any(), any(), any()) } answers {
            rows.pageAfter(firstArg(), secondArg(), thirdArg())
        }

        val out = StringBuilder()
        runBlocking { AppLog.writeLog(out) }

        assertEquals(
            """
            a:
              - 1: 1970-01-01 00:00:00.000 one
              - 2: 1970-01-01 00:00:00.000 two
            b:
              - 3: 1970-01-01 00:00:00.000 three
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun writeLog_withTag_readsOnlyTheTaggedPages() {
        val rows = groupedRows(logEntry(1, "one", "tag"))
        every { logsDao.getGroupedPageByTag("tag", any(), any(), any()) } answers {
            rows.pageAfter(secondArg(), thirdArg(), arg(3))
        }

        val out = StringBuilder()
        runBlocking { AppLog.writeLog(out, "tag") }

        assertEquals("tag:\n  - 1: 1970-01-01 00:00:00.000 one\n", out.toString())
        verify(exactly = 0) { logsDao.getGroupedPage(any(), any(), any()) }
    }

    @Test
    fun writeLog_logSubmittedWhileExporting_isInsertedOnlyAfterExportFinishes() {
        val exportStarted = CountDownLatch(1)
        val releaseExport = CountDownLatch(1)
        val inserted = CountDownLatch(1)
        every { logsDao.getGroupedPage(any(), any(), any()) } answers {
            exportStarted.countDown()
            releaseExport.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            emptyList()
        }
        every { logsDao.insert(any()) } answers { inserted.countDown() }

        val export = thread { runBlocking { AppLog.writeLog(StringBuilder()) } }
        assertTrue(exportStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        AppLog.info("tag", "message")
        assertFalse(inserted.await(BLOCKED_MILLIS, TimeUnit.MILLISECONDS))

        releaseExport.countDown()
        export.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
        assertTrue(inserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun recordingDao(expectedCalls: Int): RecordingDao {
        val recorded = CopyOnWriteArrayList<String>()
        val completed = CountDownLatch(expectedCalls)
        val record: (String) -> Unit = { call ->
            recorded += call
            completed.countDown()
        }
        every { logsDao.insert(any()) } answers { record("insert") }
        every { logsDao.deleteOlderThan(any()) } answers { record("deleteOlderThan") }
        every { logsDao.trimToNewest(any()) } answers { record("trim:${firstArg<Int>()}") }
        return RecordingDao(recorded, completed)
    }

    private fun groupedRows(vararg rows: LogEntry): List<GroupedLogRow> {
        val firstIds = rows.groupBy { it.actionId }
            .mapValues { (_, group) -> group.minOf { it.id ?: 0 }.toLong() }
        return rows.map { row ->
            GroupedLogRow(
                firstId = firstIds.getValue(row.actionId),
                id = row.id ?: 0,
                date = row.date,
                actionId = row.actionId,
                message = row.message,
            )
        }
    }

    private fun List<GroupedLogRow>.pageAfter(
        afterFirstId: Long,
        afterId: Int,
        limit: Int,
    ): List<GroupedLogRow> = filter {
        it.firstId > afterFirstId || (it.firstId == afterFirstId && it.id > afterId)
    }.take(limit)

    private fun captureNextEntry(): CapturedEntry {
        val captured = slot<LogEntry>()
        val inserted = CountDownLatch(1)
        every { logsDao.insert(capture(captured)) } answers { inserted.countDown() }
        return CapturedEntry(captured, inserted)
    }

    private fun logEntry(id: Int, message: String, actionId: String = "tag") =
        LogEntry(0, AppLog.LEVEL_INFO, actionId, message).also { it.id = id }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.messages(): Map<String, String> =
        getValue("tag") as Map<String, String>

    private data class CapturedEntry(
        private val captured: CapturingSlot<LogEntry>,
        val inserted: CountDownLatch,
    ) {
        val value: LogEntry get() = captured.captured
    }

    private class RecordingDao(
        private val recorded: List<String>,
        private val completed: CountDownLatch,
    ) {
        fun awaitCalls(): List<String> {
            assertTrue(completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            return recorded.toList()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val BLOCKED_MILLIS = 300L
        const val MAX_ENTRIES = 20_000
    }
}
