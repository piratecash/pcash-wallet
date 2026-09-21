package io.horizontalsystems.core.logger

import android.util.Log
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AppLogTest {

    @Test
    fun info_preservesLiteralMessageAndTag() {
        val entry = captureNextEntry()

        AppLog.info("literal/tag", "literal %s \$value")

        assertTrue(entry.inserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(Log.INFO, entry.value.level)
        assertEquals("literal/tag", entry.value.actionId)
        assertEquals("literal %s \$value", entry.value.message)
    }

    @Test
    fun warning_preservesLiteralMessageAndTag() {
        val entry = captureNextEntry()

        AppLog.warning("warning/tag", "literal %s \$value")

        assertTrue(entry.inserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(Log.WARN, entry.value.level)
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
        assertEquals(Log.WARN, entry.value.level)
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
        AppLog.logsDao = mockk<LogsDao>(relaxed = true).also { dao ->
            coEvery { dao.getRecent(500) } returns listOf(second, first)
            coEvery { dao.getAll() } returns listOf(first, second)
            every { dao.getByTag("tag") } returns listOf(first, second)
            every { dao.getRecentByTag("tag", 300) } returns listOf(second, first)
        }

        val taggedLogs = AppLog.getLog("tag")
        assertEquals(listOf("1", "2"), AppLog.getLog().messages().keys.toList())
        assertEquals(listOf("1", "2"), AppLog.getFullLog().messages().keys.toList())
        assertEquals(listOf("1", "2"), taggedLogs.messages().keys.toList())
        assertEquals(listOf("1", "2"), AppLog.getRecentLog("tag").messages().keys.toList())
        assertEquals(
            "1970-01-01 00:00:00.000 first\ncontinued",
            taggedLogs.messages().getValue("1"),
        )
    }

    private fun captureNextEntry(): CapturedEntry {
        val captured = slot<LogEntry>()
        val inserted = CountDownLatch(1)
        AppLog.logsDao = mockk<LogsDao>(relaxed = true).also { dao ->
            every { dao.insert(capture(captured)) } answers { inserted.countDown() }
        }
        return CapturedEntry(captured, inserted)
    }

    private fun logEntry(id: Int, message: String) =
        LogEntry(0, Log.INFO, "tag", message).also { it.id = id }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.messages(): Map<String, String> =
        getValue("tag") as Map<String, String>

    private data class CapturedEntry(
        private val captured: CapturingSlot<LogEntry>,
        val inserted: CountDownLatch,
    ) {
        val value: LogEntry get() = captured.captured
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
