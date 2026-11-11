package cash.p.terminal.core.storage

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.KoinLogsDao
import io.horizontalsystems.core.logger.AppLog
import io.horizontalsystems.core.storage.GroupedLogRow
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LogsDaoTest {

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
    }

    @After
    fun tearDown() {
        koinLogsDao.close()
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
    fun getGroupedPage_interleavedActionIds_groupsInFirstAppearanceOrder() {
        listOf("a", "b", "a", "c", "b").forEachIndexed { index, actionId ->
            dao.insert(entry(index, actionId))
        }

        assertEquals(
            listOf("a" to 1, "a" to 3, "b" to 2, "b" to 5, "c" to 4),
            dao.getGroupedPage(0, 0, Int.MAX_VALUE, LEVEL_0).rows(),
        )
    }

    @Test
    fun getGroupedPage_keysetAfterFirstGroup_resumesWithoutRepeatingRows() {
        listOf("a", "b", "a", "c", "b").forEachIndexed { index, actionId ->
            dao.insert(entry(index, actionId))
        }
        val firstPage = dao.getGroupedPage(0, 0, 2, LEVEL_0)

        val secondPage = dao.getGroupedPage(firstPage.last().firstId, firstPage.last().id, 2, LEVEL_0)

        assertEquals(listOf("a" to 1, "a" to 3), firstPage.rows())
        assertEquals(listOf("b" to 2, "b" to 5), secondPage.rows())
    }

    @Test
    fun getGroupedPageByTag_actionIdSubstring_returnsOnlyMatchingGroups() {
        listOf("Zcash-sync", "Bitcoin-sync", "Zcash-send").forEachIndexed { index, actionId ->
            dao.insert(entry(index, actionId))
        }

        assertEquals(
            listOf("Zcash-sync" to 1, "Zcash-send" to 3),
            dao.getGroupedPageByTag("Zcash", 0, 0, Int.MAX_VALUE, LEVEL_0).rows(),
        )
    }

    @Test
    fun getRecent_rowsOfTwoLevels_eachLevelSeesOnlyItsOwnRows() {
        insertAtLevels()

        assertEquals(
            listOf("message-4", "message-3", "message-0"),
            runBlocking { dao.getRecent(Int.MAX_VALUE, LEVEL_0) }.map { it.message },
        )
        assertEquals(
            listOf("message-2", "message-1"),
            runBlocking { dao.getRecent(Int.MAX_VALUE, LEVEL_1) }.map { it.message },
        )
    }

    @Test
    fun getRecentByTag_rowsOfTwoLevels_eachLevelSeesOnlyItsOwnRows() {
        insertAtLevels()

        assertEquals(
            listOf("message-4", "message-0"),
            dao.getRecentByTag("Zcash", Int.MAX_VALUE, LEVEL_0).map { it.message },
        )
        assertEquals(
            listOf("message-2", "message-1"),
            dao.getRecentByTag("Zcash", Int.MAX_VALUE, LEVEL_1).map { it.message },
        )
    }

    @Test
    fun getGroupedPage_groupsSpanningLevels_eachLevelSeesItsOwnRowsInItsOwnGroupOrder() {
        insertAtLevels()

        assertEquals(
            listOf("Zcash-sync" to 1, "Bitcoin-sync" to 4, "Zcash-send" to 5),
            dao.getGroupedPage(0, 0, Int.MAX_VALUE, LEVEL_0).rows(),
        )
        assertEquals(
            listOf("Zcash-send" to 2, "Zcash-sync" to 3),
            dao.getGroupedPage(0, 0, Int.MAX_VALUE, LEVEL_1).rows(),
        )
    }

    @Test
    fun getGroupedPageByTag_groupsSpanningLevels_eachLevelSeesItsOwnRowsInItsOwnGroupOrder() {
        insertAtLevels()

        assertEquals(
            listOf("Zcash-sync" to 1, "Zcash-send" to 5),
            dao.getGroupedPageByTag("Zcash", 0, 0, Int.MAX_VALUE, LEVEL_0).rows(),
        )
        assertEquals(
            listOf("Zcash-send" to 2, "Zcash-sync" to 3),
            dao.getGroupedPageByTag("Zcash", 0, 0, Int.MAX_VALUE, LEVEL_1).rows(),
        )
    }

    @Test
    fun writeLog_rowsOfTwoLevels_exportsOnlyTheCurrentLevel() {
        insertAtLevels()
        koinLogsDao.dao = dao
        koinLogsDao.scope.userLevel = LEVEL_1

        val out = StringBuilder()
        runBlocking { AppLog.writeLog(out) }

        assertEquals(
            "Zcash-send:\n  - 2: 1970-01-01 00:00:00.001 message-1\n" +
                "Zcash-sync:\n  - 3: 1970-01-01 00:00:00.002 message-2\n",
            out.toString(),
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
        koinLogsDao.dao = dao

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

    /** Both Zcash groups span the levels: a first-appearance order that ignores the level comes out different. */
    private fun insertAtLevels() {
        listOf(
            "Zcash-sync" to LEVEL_0,
            "Zcash-send" to LEVEL_1,
            "Zcash-sync" to LEVEL_1,
            "Bitcoin-sync" to LEVEL_0,
            "Zcash-send" to LEVEL_0,
        ).forEachIndexed { index, (actionId, level) ->
            dao.insert(entry(index, actionId).copy(userLevel = level))
        }
    }

    private fun insertEntries(count: Int) = repeat(count) { dao.insert(entry(it, "tag")) }

    private fun entry(index: Int, actionId: String) = LogEntry(
        date = index.toLong(),
        level = Log.INFO,
        actionId = actionId,
        message = "message-$index",
    )

    private fun storedMessages() =
        runBlocking { dao.getRecent(Int.MAX_VALUE, LEVEL_0) }.reversed().map { it.message }

    private fun List<GroupedLogRow>.rows(): List<Pair<String, Int>> =
        map { it.actionId to it.id }

    private fun groupName(index: Int) = "action-%05d".format(index)

    private companion object {
        const val LEVEL_0 = 0
        const val LEVEL_1 = 1
        const val GROUPS = 4_000
        const val ENTRIES_PER_GROUP = 5
        const val ENTRY_PREFIX = "  - "
    }
}
