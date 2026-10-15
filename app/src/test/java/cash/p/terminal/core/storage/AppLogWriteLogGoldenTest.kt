package cash.p.terminal.core.storage

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.KoinLogsDao
import io.horizontalsystems.core.logger.AppLog
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Goldens were captured on the cursor-based export; any change in the exported text fails here. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppLogWriteLogGoldenTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: LogsDao
    private val koinLogsDao = KoinLogsDao()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.logsDao()
        koinLogsDao.dao = dao
    }

    @After
    fun tearDown() {
        koinLogsDao.close()
        database.close()
    }

    @Test
    fun writeLog_smallFixtureAllGroups_matchesGolden() {
        insertSmallFixture()

        assertEquals(readGolden("small-all.txt"), writeLogText())
    }

    @Test
    fun writeLog_smallFixtureSingleTag_matchesGolden() {
        insertSmallFixture()

        assertEquals(readGolden("small-tag.txt"), writeLogText(SMALL_TAG))
    }

    @Test
    fun writeLog_largeFixtureCrossingPageBoundary_matchesGolden() {
        insertLargeFixture()

        assertEquals(readGolden("large-all.txt"), writeLogText())
    }

    @Test
    fun writeLog_largeFixtureSingleTagCrossingPageBoundary_matchesGolden() {
        insertLargeFixture()

        assertEquals(readGolden("large-tag.txt"), writeLogText(LARGE_TAG))
    }

    private fun writeLogText(tag: String? = null): String {
        val out = StringBuilder()
        runBlocking { AppLog.writeLog(out, tag) }
        return out.toString()
    }

    private fun insertSmallFixture() = database.runInTransaction {
        (1..SMALL_FIXTURE_SIZE).forEach { id ->
            val actionId = SMALL_GROUPS[(id - 1) % SMALL_GROUPS.size]
            dao.insert(fixtureEntry(id, actionId))
        }
    }

    private fun insertLargeFixture() = database.runInTransaction {
        (1..LARGE_FIXTURE_SIZE).forEach { id ->
            dao.insert(fixtureEntry(id, largeFixtureActionId(id)))
        }
    }

    private fun largeFixtureActionId(id: Int): String = when (id) {
        in 1..200 -> "Normal-0"
        in 201..500 -> "Cross1"
        in 501..1300 -> "Cross2"
        in 1301..1600 -> "Normal-1"
        else -> "Cross3"
    }

    private fun fixtureEntry(id: Int, actionId: String) = LogEntry(
        date = id * 1000L,
        level = Log.INFO,
        actionId = actionId,
        message = "msg-$id",
    )

    private fun readGolden(fileName: String): String {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("applog/$fileName")) {
            "Missing golden resource: applog/$fileName"
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private companion object {
        const val SMALL_FIXTURE_SIZE = 30
        const val LARGE_FIXTURE_SIZE = 2000
        const val SMALL_TAG = "Alpha"

        /** Matches Cross1/Cross2/Cross3 (1500 rows): group Cross2 alone straddles both the
         *  tag-filtered page boundary (rows 301-1100 of the filtered output cross row 500)
         *  and the all-groups page boundary (ids 501-1300 cross row 1000). */
        const val LARGE_TAG = "Cross"
        val SMALL_GROUPS = listOf("Alpha", "Beta", "Gamma")
    }
}
