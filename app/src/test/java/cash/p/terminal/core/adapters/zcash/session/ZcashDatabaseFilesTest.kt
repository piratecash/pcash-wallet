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

    private fun writeDatabase(): List<File> =
        listOf("", "-wal", "-shm", "-journal").map { suffix ->
            File(databaseFiles.databaseFile(ACCOUNT_ID).path + suffix).apply { writeText("data") }
        }
}
