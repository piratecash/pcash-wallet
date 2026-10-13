package io.horizontalsystems.core.storage

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
        "SELECT l.id, l.date, l.actionId, l.message FROM LogEntry l " +
            "JOIN (SELECT actionId, MIN(id) AS firstId FROM LogEntry GROUP BY actionId) g " +
            "ON l.actionId = g.actionId " +
            "ORDER BY g.firstId, l.id"
    )
    fun streamGrouped(): Cursor

    @Query(
        "SELECT l.id, l.date, l.actionId, l.message FROM LogEntry l " +
            "JOIN (SELECT actionId, MIN(id) AS firstId FROM LogEntry " +
            "WHERE actionId LIKE '%' || :tag || '%' GROUP BY actionId) g " +
            "ON l.actionId = g.actionId " +
            "ORDER BY g.firstId, l.id"
    )
    fun streamGroupedByTag(tag: String): Cursor

    @Query("DELETE FROM LogEntry WHERE date < :timestamp")
    fun deleteOlderThan(timestamp: Long)

    /** Keeps exactly the newest [keep] rows by count; no-op when the table holds no more than that. */
    @Query("DELETE FROM LogEntry WHERE id <= (SELECT id FROM LogEntry ORDER BY id DESC LIMIT 1 OFFSET :keep)")
    fun trimToNewest(keep: Int)

}
