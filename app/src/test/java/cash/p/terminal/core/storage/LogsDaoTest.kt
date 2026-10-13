package cash.p.terminal.core.storage

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.core.logger.AppLog
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LogsDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: LogsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.logsDao()
    }

    @After
    fun tearDown() {
        AppLog.logsDao = mockk(relaxed = true)
        database.close()
    }

    @Test
    fun trimToNewest_moreRowsThanCap_keepsExactlyTheNewestRows() {
        insertEntries(5)

        dao.trimToNewest(2)

        assertEquals(listOf("message-3", "message-4"), storedMessages())
    }

    @Test
    fun trimToNewest_rowCountEqualToCap_keepsAllRows() {
        insertEntries(3)

        dao.trimToNewest(3)

        assertEquals(listOf("message-0", "message-1", "message-2"), storedMessages())
    }

    @Test
    fun trimToNewest_emptyTable_doesNothing() {
        dao.trimToNewest(10)

        assertEquals(emptyList<String>(), storedMessages())
    }

    @Test
    fun trimToNewest_idGapsWithRowCountBelowCap_keepsAllRows() {
        insertEntries(5)
        dao.deleteOlderThan(timestamp = 3)

        dao.trimToNewest(10)

        assertEquals(listOf("message-3", "message-4"), storedMessages())
    }

    @Test
    fun streamGrouped_interleavedActionIds_groupsInFirstAppearanceOrder() {
        listOf("a", "b", "a", "c", "b").forEachIndexed { index, actionId ->
            dao.insert(entry(index, actionId))
        }

        assertEquals(
            listOf("a" to 1, "a" to 3, "b" to 2, "b" to 5, "c" to 4),
            dao.streamGrouped().readRows(),
        )
    }

    @Test
    fun streamGroupedByTag_actionIdSubstring_returnsOnlyMatchingGroups() {
        listOf("Zcash-sync", "Bitcoin-sync", "Zcash-send").forEachIndexed { index, actionId ->
            dao.insert(entry(index, actionId))
        }

        assertEquals(
            listOf("Zcash-sync" to 1, "Zcash-send" to 3),
            dao.streamGroupedByTag("Zcash").readRows(),
        )
    }

    @Test
    fun writeLog_manyActionIdGroups_writesEveryGroupAndEntryInOrder() {
        database.runInTransaction {
            repeat(GROUPS) { group ->
                repeat(ENTRIES_PER_GROUP) { entryIndex ->
                    dao.insert(
                        LogEntry(
                            date = 0,
                            level = Log.INFO,
                            actionId = groupName(group),
                            message = "message-$entryIndex",
                        )
                    )
                }
            }
        }
        AppLog.logsDao = dao

        val out = StringBuilder()
        runBlocking { AppLog.writeLog(out) }

        val lines = out.toString().trimEnd().lines()
        assertEquals(GROUPS * (ENTRIES_PER_GROUP + 1), lines.size)
        assertEquals(
            (0 until GROUPS).map { "${groupName(it)}:" },
            lines.filterNot { it.startsWith(ENTRY_PREFIX) },
        )
        assertEquals(
            (1..GROUPS * ENTRIES_PER_GROUP).toList(),
            lines.filter { it.startsWith(ENTRY_PREFIX) }
                .map { it.removePrefix(ENTRY_PREFIX).substringBefore(':').toInt() },
        )
    }

    private fun insertEntries(count: Int) = repeat(count) { dao.insert(entry(it, "tag")) }

    private fun entry(index: Int, actionId: String) = LogEntry(
        date = index.toLong(),
        level = Log.INFO,
        actionId = actionId,
        message = "message-$index",
    )

    private fun storedMessages() =
        runBlocking { dao.getRecent(Int.MAX_VALUE) }.reversed().map { it.message }

    private fun Cursor.readRows(): List<Pair<String, Int>> = use { cursor ->
        val actionIdIndex = cursor.getColumnIndexOrThrow("actionId")
        val idIndex = cursor.getColumnIndexOrThrow("id")
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(actionIdIndex) to cursor.getInt(idIndex))
            }
        }
    }

    private fun groupName(index: Int) = "action-%05d".format(index)

    private companion object {
        const val GROUPS = 4_000
        const val ENTRIES_PER_GROUP = 5
        const val ENTRY_PREFIX = "  - "
    }
}
