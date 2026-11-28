package cash.p.terminal.feature.logging.journal

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalStoreTest {
    private val directory = createTempDirectory("journal").toFile()
    private val file = File(directory, "log")
    private val database = Room.databaseBuilder<JournalDatabase>(file.absolutePath, ::JournalDatabase_Impl)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val store = JournalStore(database)

    @AfterTest
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun insert_photosPastTheSizeBound_dropsOldestPhotosAndKeepsEveryRecord() = runBlocking<Unit> {
        val count = (JournalStore.MAX_BYTES / PHOTO_SIZE).toInt() + EXTRA
        repeat(count) { store.insert(it.toLong(), JournalRecordKind.FAILED, "", Random.nextBytes(PHOTO_SIZE)) }

        val records = store.all().sortedBy { it.timestamp }
        assertEquals(count, records.size)
        assertTrue(records.first().photoRemoved)
        assertNull(store.photo(records.first().id))
        assertTrue(!records.last().photoRemoved)
        assertNotNull(store.photo(records.last().id))
        database.close()
        assertTrue(file.length() <= JournalStore.MAX_BYTES, "${file.length()} bytes")
    }

    @Test
    fun insert_pastTheSizeBoundWithNoPhotoLeft_dropsOldestRecords() = runBlocking<Unit> {
        val accountId = "a".repeat(LARGE_ROW)
        val count = (JournalStore.MAX_BYTES / LARGE_ROW).toInt() + EXTRA
        repeat(count) { store.insert(it.toLong(), JournalRecordKind.SUCCESS, accountId, null) }

        val timestamps = store.all().map { it.timestamp }
        assertTrue(timestamps.size < count)
        assertEquals(count - 1L, timestamps.max())
        assertTrue(0L !in timestamps)
        database.close()
        assertTrue(file.length() <= JournalStore.MAX_BYTES, "${file.length()} bytes")
    }

    @Test
    fun insert_fileLeftOversizedByAFailedVacuum_compactsIt() = runBlocking<Unit> {
        keepFreedPages()
        val filler = "INSERT INTO filler VALUES (zeroblob(${JournalStore.MAX_BYTES + PHOTO_SIZE}))"
        database.useWriterConnection { connection ->
            listOf("CREATE TABLE filler(data BLOB)", filler, "DROP TABLE filler")
                .forEach { sql -> connection.usePrepared(sql) { it.step() } }
        }

        store.insert(RECENT, JournalRecordKind.SUCCESS, "account", null)

        database.close()
        assertTrue(file.length() <= JournalStore.MAX_BYTES, "${file.length()} bytes")
    }

    @Test
    fun insert_fullJournalWhileVacuumFails_neverGrowsPastTheBound() = runBlocking<Unit> {
        keepFreedPages()
        repeat((JournalStore.MAX_BYTES / PHOTO_SIZE).toInt() + EXTRA) {
            store.insert(it.toLong(), JournalRecordKind.FAILED, "", Random.nextBytes(PHOTO_SIZE))
        }
        val failingVacuum = spyk(store)
        coEvery { failingVacuum.vacuum() } throws IllegalStateException("test")

        failingVacuum.insert(RECENT, JournalRecordKind.FAILED, "", Random.nextBytes(PHOTO_SIZE))

        database.close()
        assertTrue(file.length() <= JournalStore.MAX_BYTES, "${file.length()} bytes")
    }

    @Test
    fun insert_fullJournalImportRetried_removesNoPhoto() = runBlocking<Unit> {
        val count = (JournalStore.MAX_BYTES / PHOTO_SIZE).toInt() + EXTRA
        repeat(count) {
            store.insert(it.toLong(), JournalRecordKind.DURESS_ENTRY, "", Random.nextBytes(PHOTO_SIZE), it.toLong())
        }
        val photosBefore = store.all().count { !it.photoRemoved }

        val id = store.insert(RECENT, JournalRecordKind.DURESS_ENTRY, "", Random.nextBytes(PHOTO_SIZE), count - 1L)

        assertEquals(-1L, id)
        assertEquals(photosBefore, store.all().count { !it.photoRemoved })
    }

    /** As on SQLCipher, where freed pages stay in the file until a VACUUM; the bundled driver defaults otherwise. */
    private suspend fun keepFreedPages() = database.useWriterConnection { connection ->
        listOf("PRAGMA auto_vacuum = NONE", "VACUUM").forEach { sql -> connection.usePrepared(sql) { it.step() } }
    }

    @Test
    fun deleteOlderThan_mixedRecords_removesOnlyOlderOnesWithTheirPhotos() = runBlocking<Unit> {
        val old = store.insert(OLD, JournalRecordKind.FAILED, "", byteArrayOf(1))
        val recent = store.insert(RECENT, JournalRecordKind.SUCCESS, "account", byteArrayOf(2))

        store.deleteOlderThan(CUTOFF)

        assertEquals(listOf(recent), store.all().map { it.id })
        assertNull(store.photo(old))
        assertNotNull(store.photo(recent))
    }

    @Test
    fun page_insertedOutOfEventOrder_newestEventFirstAndOldestTrimmedFirst() = runBlocking<Unit> {
        store.insert(RECENT, JournalRecordKind.FAILED, "", Random.nextBytes(PHOTO_SIZE))
        store.insert(OLD, JournalRecordKind.DURESS_ENTRY, "", Random.nextBytes(PHOTO_SIZE))

        assertEquals(listOf(RECENT, OLD), store.page(limit = 10, offset = 0).map { it.timestamp })
        repeat((JournalStore.MAX_BYTES / PHOTO_SIZE).toInt()) {
            store.insert(RECENT + 1 + it, JournalRecordKind.FAILED, "", Random.nextBytes(PHOTO_SIZE))
        }
        val byTime = store.all().associateBy { it.timestamp }
        assertTrue(byTime.getValue(OLD).photoRemoved)
    }

    @Test
    fun deleteAll_recordsWithPhotos_leavesNothing() = runBlocking<Unit> {
        val id = store.insert(RECENT, JournalRecordKind.DURESS_ENTRY, "", byteArrayOf(1))

        store.deleteAll()

        assertTrue(store.all().isEmpty())
        assertNull(store.photo(id))
    }

    private companion object {
        const val PHOTO_SIZE = 196_608
        const val LARGE_ROW = 128 * 1024
        const val EXTRA = 8
        const val OLD = 1_000L
        const val CUTOFF = 2_000L
        const val RECENT = 3_000L
    }
}
