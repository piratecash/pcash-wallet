package cash.p.terminal.feature.logging.journal

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

enum class JournalRecordKind { SUCCESS, FAILED, DURESS_ENTRY }

@Entity(indices = [Index(value = ["kind", "sourceSeq"], unique = true)])
data class JournalRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val kind: JournalRecordKind,
    val accountId: String,
    val hasPhoto: Boolean,
    val photoRemoved: Boolean = false,
    /** The mailbox entry it was imported from; null for the level's own entries. */
    val sourceSeq: Long? = null,
)

/** Goes with its record: a deleted record never leaves its photo behind. */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = JournalRecord::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class JournalPhoto(
    @PrimaryKey
    val recordId: Long,
    val jpeg: ByteArray,
)

@Dao
interface JournalDao {
    /** -1 for an imported entry already in the journal. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: JournalRecord): Long

    @Insert
    suspend fun insertPhoto(photo: JournalPhoto)

    @Transaction
    suspend fun insert(record: JournalRecord, jpeg: ByteArray?): Long {
        val id = insertRecord(record.copy(hasPhoto = jpeg != null))
        if (id != NOT_INSERTED) jpeg?.let { insertPhoto(JournalPhoto(id, it)) }
        return id
    }

    @Query("SELECT EXISTS(SELECT 1 FROM JournalRecord WHERE kind = :kind AND sourceSeq = :sourceSeq)")
    suspend fun hasImported(kind: JournalRecordKind, sourceSeq: Long): Boolean

    // Imports arrive out of event order, so the id says nothing about age.
    @Query("SELECT * FROM JournalRecord ORDER BY timestamp DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<JournalRecord>

    @Query("SELECT COUNT(*) FROM JournalRecord")
    fun observeCount(): Flow<Int>

    @Query("SELECT jpeg FROM JournalPhoto WHERE recordId = :recordId")
    suspend fun photo(recordId: Long): ByteArray?

    @Query(
        "SELECT p.recordId FROM JournalPhoto p JOIN JournalRecord r ON r.id = p.recordId " +
            "ORDER BY r.timestamp, r.id LIMIT 1",
    )
    suspend fun oldestPhotoRecordId(): Long?

    @Query("SELECT id FROM JournalRecord ORDER BY timestamp, id LIMIT 1")
    suspend fun oldestRecordId(): Long?

    @Query("UPDATE JournalRecord SET photoRemoved = 1 WHERE id = :recordId")
    suspend fun markPhotoRemoved(recordId: Long)

    @Query("DELETE FROM JournalPhoto WHERE recordId = :recordId")
    suspend fun deletePhoto(recordId: Long)

    @Query("DELETE FROM JournalRecord WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM JournalRecord")
    suspend fun deleteAll()

    @Query("DELETE FROM JournalRecord WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Transaction
    suspend fun removePhoto(recordId: Long) {
        deletePhoto(recordId)
        markPhotoRemoved(recordId)
    }
}

internal const val NOT_INSERTED = -1L

/** A level's login journal on desktop: the records and their photos, nothing else. */
@Database(version = 1, exportSchema = false, entities = [JournalRecord::class, JournalPhoto::class])
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
}
