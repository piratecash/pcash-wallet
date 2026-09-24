package cash.p.terminal.core.adapters.zcash.session

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

private const val ACCOUNT_ID = "account"
private const val PRISTINE_SUFFIX = ".pristine"

class ZcashDatabaseFilesTest {

    private lateinit var noBackupDir: File
    private lateinit var databaseFiles: ZcashDatabaseFiles

    @Before
    fun setUp() {
        noBackupDir = Files.createTempDirectory("zcash-db").toFile()
        databaseFiles = ZcashDatabaseFiles(mockk<Context> { every { noBackupFilesDir } returns noBackupDir })
        databaseFiles.dataDir.mkdirs()
    }

    @After
    fun tearDown() {
        noBackupDir.deleteRecursively()
    }

    @Test
    fun delete_existingDatabase_removesEveryCompanionFile() {
        val files = writeDatabase()

        assertTrue(databaseFiles.delete(ACCOUNT_ID))
        assertTrue(files.none { it.exists() })
    }

    @Test
    fun delete_alreadyCleanAccount_reportsSuccess() {
        assertTrue(databaseFiles.delete(ACCOUNT_ID))
    }

    @Test
    fun delete_undeletableCompanionFile_reportsFailure() {
        writeDatabase()
        // A non-empty directory is what File.delete() refuses, standing in for a locked file.
        val wal = File(databaseFiles.databaseFile(ACCOUNT_ID).path + "-wal")
        wal.delete()
        wal.mkdirs()
        File(wal, "child").writeText("busy")

        assertFalse(databaseFiles.delete(ACCOUNT_ID))
        assertFalse(databaseFiles.databaseFile(ACCOUNT_ID).exists())
    }

    @Test
    fun markPristine_thenIsPristine_returnsTrue() {
        databaseFiles.markPristine(ACCOUNT_ID)

        assertTrue(databaseFiles.isPristine(ACCOUNT_ID))
    }

    @Test
    fun isPristine_neverMarked_returnsFalse() {
        assertFalse(databaseFiles.isPristine(ACCOUNT_ID))
    }

    @Test
    fun delete_markedAccount_removesTheMarkWithTheDatabase() {
        val files = writeDatabase()
        databaseFiles.markPristine(ACCOUNT_ID)

        assertTrue(databaseFiles.delete(ACCOUNT_ID))
        assertFalse(databaseFiles.isPristine(ACCOUNT_ID))
        assertTrue(files.none { it.exists() })
    }

    @Test
    fun delete_undeletableMark_keepsTheDatabaseAndReportsFailure() {
        writeDatabase()
        // A non-empty directory is what File.delete() refuses, standing in for a locked file.
        val mark = File(databaseFiles.databaseFile(ACCOUNT_ID).path + PRISTINE_SUFFIX)
        mark.mkdirs()
        File(mark, "child").writeText("busy")

        assertFalse(databaseFiles.delete(ACCOUNT_ID))
        assertTrue(databaseFiles.databaseFile(ACCOUNT_ID).exists())
    }

    @Test
    fun markPristine_dataDirCannotBeCreated_doesNotThrow() {
        databaseFiles.dataDir.deleteRecursively()
        databaseFiles.dataDir.writeText("not a directory")

        databaseFiles.markPristine(ACCOUNT_ID)

        assertFalse(databaseFiles.isPristine(ACCOUNT_ID))
    }

    private fun writeDatabase(): List<File> =
        listOf("", "-wal", "-shm", "-journal").map { suffix ->
            File(databaseFiles.databaseFile(ACCOUNT_ID).path + suffix).apply { writeText("data") }
        }
}
