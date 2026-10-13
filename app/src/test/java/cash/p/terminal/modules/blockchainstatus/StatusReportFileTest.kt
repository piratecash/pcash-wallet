package cash.p.terminal.modules.blockchainstatus

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.KoinLogsDao
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.AppDatabase
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StatusReportFileTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: LogsDao
    private val koinLogsDao = KoinLogsDao()
    private lateinit var statusReportFile: StatusReportFile
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.logsDao()
        koinLogsDao.dao = dao

        val dispatcher = UnconfinedTestDispatcher()
        statusReportFile = StatusReportFile(context, TestDispatcherProvider(dispatcher, TestScope(dispatcher)))
    }

    @After
    fun tearDown() {
        koinLogsDao.close()
        database.close()
    }

    @Test
    fun write_headerLogAndFooter_fileContainsHeaderEveryEntryInOrderAndFooter() = runTest {
        listOf("Zcash-sync" to "started", "Bitcoin-send" to "broadcast", "Zcash-sync" to "finished")
            .forEachIndexed { index, (actionId, message) ->
                dao.insert(LogEntry(date = index.toLong(), level = Log.INFO, actionId = actionId, message = message))
            }

        val file = statusReportFile.write(
            fileName = "report.txt",
            header = "HEADER\n",
            logTag = null,
            footer = "FOOTER\n",
        )

        val content = file.readText()
        assertTrue(content.startsWith("HEADER\n"))
        assertTrue(content.endsWith("FOOTER\n"))

        val logSection = content.removePrefix("HEADER\n").removeSuffix("FOOTER\n")
        assertEquals(
            """
            Zcash-sync:
              - 1: 1970-01-01 00:00:00.000 started
              - 3: 1970-01-01 00:00:00.002 finished
            Bitcoin-send:
              - 2: 1970-01-01 00:00:00.001 broadcast
            """.trimIndent() + "\n",
            logSection,
        )
    }

    @Test
    fun write_withTag_filtersLogToTheTag() = runTest {
        dao.insert(LogEntry(date = 0, level = Log.INFO, actionId = "Zcash-sync", message = "started"))
        dao.insert(LogEntry(date = 1, level = Log.INFO, actionId = "Bitcoin-send", message = "broadcast"))

        val file = statusReportFile.write(fileName = "zcash_report.txt", header = "", logTag = "Zcash", footer = "")

        val content = file.readText()
        assertTrue(content.contains("Zcash-sync"))
        assertFalse(content.contains("Bitcoin-send"))
    }

    @Test
    fun write_always_leavesNoTmpFileBehind() = runTest {
        dao.insert(LogEntry(date = 0, level = Log.INFO, actionId = "tag", message = "message"))

        statusReportFile.write(fileName = "report.txt", header = "", logTag = null, footer = "")

        assertFalse(File(context.cacheDir, "report.txt.tmp").exists())
    }

    @Test
    fun share_calledWhileAnotherShareIsRunning_returnsNullWithoutOverlappingTheFirst() {
        val cursorReached = CountDownLatch(1)
        val releaseCursor = CountDownLatch(1)
        val blockingCursor = mockk<Cursor>(relaxed = true).also { cursor ->
            every { cursor.moveToNext() } answers {
                cursorReached.countDown()
                releaseCursor.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                false
            }
        }
        koinLogsDao.dao = mockk<LogsDao>(relaxed = true).also { logsDao ->
            every { logsDao.streamGrouped() } returns blockingCursor
        }

        var firstUri: Uri? = null
        val firstDone = CountDownLatch(1)
        thread {
            firstUri = runBlocking {
                statusReportFile.share(fileName = "first.txt", header = "", logTag = null, footer = "")
            }
            firstDone.countDown()
        }
        assertTrue(cursorReached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val second = runBlocking {
            statusReportFile.share(fileName = "second.txt", header = "", logTag = null, footer = "")
        }
        assertNull(second)

        releaseCursor.countDown()
        assertTrue(firstDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(firstUri != null)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
