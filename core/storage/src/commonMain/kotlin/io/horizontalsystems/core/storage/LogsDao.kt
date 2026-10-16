package io.horizontalsystems.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** One exported row: [firstId] is the group's keyset part, [id] the row's. */
data class GroupedLogRow(
    val firstId: Long,
    val id: Int,
    val date: Long,
    val actionId: String,
    val message: String,
)

@Dao
interface LogsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(logEntry: LogEntry)

    @Query("SELECT * FROM LogEntry ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<LogEntry>

    @Query("SELECT * FROM LogEntry WHERE actionId LIKE '%' || :tag || '%' ORDER BY id DESC LIMIT :limit")
    fun getRecentByTag(tag: String, limit: Int): List<LogEntry>

    /** Groups ordered by first appearance, entries by id — same grouping the log export needs. */
    @Query(
        "SELECT g.firstId AS firstId, l.id AS id, l.date AS date, l.actionId AS actionId, " +
            "l.message AS message FROM LogEntry l " +
            "JOIN (SELECT actionId, MIN(id) AS firstId FROM LogEntry GROUP BY actionId) g " +
            "ON l.actionId = g.actionId " +
            "WHERE g.firstId > :afterFirstId OR (g.firstId = :afterFirstId AND l.id > :afterId) " +
            "ORDER BY g.firstId, l.id LIMIT :limit"
    )
    fun getGroupedPage(afterFirstId: Long, afterId: Int, limit: Int): List<GroupedLogRow>

    @Query(
        "SELECT g.firstId AS firstId, l.id AS id, l.date AS date, l.actionId AS actionId, " +
            "l.message AS message FROM LogEntry l " +
            "JOIN (SELECT actionId, MIN(id) AS firstId FROM LogEntry " +
            "WHERE actionId LIKE '%' || :tag || '%' GROUP BY actionId) g " +
            "ON l.actionId = g.actionId " +
            "WHERE g.firstId > :afterFirstId OR (g.firstId = :afterFirstId AND l.id > :afterId) " +
            "ORDER BY g.firstId, l.id LIMIT :limit"
    )
    fun getGroupedPageByTag(
        tag: String,
        afterFirstId: Long,
        afterId: Int,
        limit: Int,
    ): List<GroupedLogRow>

    @Query("DELETE FROM LogEntry WHERE date < :timestamp")
    fun deleteOlderThan(timestamp: Long)

    /** Keeps exactly the newest [keep] rows by count; no-op when the table holds no more than that. */
    @Query("DELETE FROM LogEntry WHERE id <= (SELECT id FROM LogEntry ORDER BY id DESC LIMIT 1 OFFSET :keep)")
    fun trimToNewest(keep: Int)

}
