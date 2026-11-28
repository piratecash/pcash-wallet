package cash.p.terminal.feature.logging.journal

import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** Every removal ends in VACUUM: the pages of a deleted photo must not stay in the file. */
class JournalStore(private val database: JournalDatabase) {
    private val dao = database.journalDao()
    private val logger = Logger.withTag("JournalStore")

    val count: Flow<Int> get() = dao.observeCount()

    /**
     * No record cap: the oldest photos go first, and records only once no photo is left. Room is made before the
     * insert, which reuses the freed pages, so the file stays in bound even when the VACUUM after it fails.
     * An imported entry carries its mailbox [sourceSeq] and is written once however often it is imported.
     */
    suspend fun insert(
        timestamp: Long,
        kind: JournalRecordKind,
        accountId: String,
        photo: ByteArray?,
        sourceSeq: Long? = null,
    ): Long {
        // Checked first: trimming for an entry already written would delete older photos for nothing.
        if (sourceSeq != null && dao.hasImported(kind, sourceSeq)) return NOT_INSERTED
        val record = JournalRecord(
            timestamp = timestamp,
            kind = kind,
            accountId = accountId,
            hasPhoto = false,
            sourceSeq = sourceSeq,
        )
        val trimmed = trimToFit(spaceFor(accountId.length + (photo?.size ?: 0)))
        val id = dao.insert(record, photo)
        // The file, not its content: a VACUUM that failed once leaves free pages that only a later one drops.
        if (trimmed || pragma("page_count") * pragma("page_size") > MAX_BYTES) shrink()
        return id
    }

    suspend fun page(limit: Int, offset: Int): List<JournalRecord> = dao.page(limit, offset)

    suspend fun all(): List<JournalRecord> = dao.page(limit = Int.MAX_VALUE, offset = 0)

    suspend fun photo(recordId: Long): ByteArray? = dao.photo(recordId)

    suspend fun deleteById(id: Long) = removing { dao.deleteRecord(id) }

    suspend fun deleteAll() = removing { dao.deleteAll() }

    suspend fun deleteOlderThan(cutoff: Long) = removing { dao.deleteOlderThan(cutoff) }

    private suspend fun trimToFit(incoming: Long): Boolean {
        var trimmed = false
        while (usedBytes() + incoming > MAX_BYTES && removeOldest()) trimmed = true
        return trimmed
    }

    private suspend fun removeOldest(): Boolean {
        dao.oldestPhotoRecordId()?.let {
            dao.removePhoto(it)
            return true
        }
        val oldest = dao.oldestRecordId() ?: return false
        dao.deleteRecord(oldest)
        return true
    }

    private suspend inline fun removing(block: () -> Unit) {
        block()
        vacuum()
    }

    private suspend fun shrink() = try {
        vacuum()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Shrinking the journal failed" }
    }

    internal suspend fun vacuum() =
        database.useWriterConnection { connection -> connection.usePrepared("VACUUM") { it.step() } }

    private suspend fun usedBytes(): Long =
        (pragma("page_count") - pragma("freelist_count")) * pragma("page_size")

    private suspend fun pragma(name: String): Long = database.useWriterConnection { connection ->
        connection.usePrepared("PRAGMA $name") {
            it.step()
            it.getLong(0)
        }
    }

    companion object {
        /** Below the largest decoy `log.N`, so a full journal never stands out by its size. */
        const val MAX_BYTES = 56L * 1024 * 1024

        /** Overflow pages carry a little less than a page (SQLCipher reserves part of each), and the tree may split. */
        private fun spaceFor(payload: Int): Long = payload + payload / PAGE_OVERHEAD_SHARE + INSERT_SLACK

        private const val PAGE_OVERHEAD_SHARE = 32
        private const val INSERT_SLACK = 64L * 1024
    }
}
