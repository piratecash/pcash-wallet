package cash.p.terminal.core.adapters.zcash.session

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun diagnostics_databaseAbsent_reportsDirAndDbState() {
        val line = databaseFiles.diagnostics(ACCOUNT_ID)

        assertTrue(line.startsWith("db-fs: "))
        listOf("dir=exists", "db=absent", "journal=absent", "wal=absent").forEach { assertTrue(it in line) }
    }

    @Test
    fun diagnostics_databaseAndJournalPresent_reportsSizes() {
        val files = writeDatabase()
        files.first().writeText("12345")
        files.last().writeText("123")

        val line = databaseFiles.diagnostics(ACCOUNT_ID)

        assertTrue("db=exists size=5" in line)
        assertTrue("journal=exists size=3" in line)
    }

    @Test
    fun diagnostics_dataDirMissing_reportsDirAbsentAndNeverThrows() {
        databaseFiles.dataDir.deleteRecursively()

        val line = databaseFiles.diagnostics(ACCOUNT_ID)

        assertTrue("dir=absent" in line)
        assertTrue("db=absent" in line)
    }

    @Test
    fun diagnostics_osProbesUnavailable_fallsBackToNa() {
        val line = databaseFiles.diagnostics(ACCOUNT_ID)

        listOf("ro=n/a", "free=n/a", "inodes=n/a", "uid=n/a").forEach { assertTrue(it in line) }
        assertTrue(Regex("fds=(\\d+/\\d+|n/a)").containsMatchIn(line))
    }

    @Test
    fun diagnostics_always_containsNoAbsolutePath() {
        writeDatabase()

        assertFalse(noBackupDir.path in databaseFiles.diagnostics(ACCOUNT_ID))
    }

    @Test
    fun mountFor_nestedMountpoints_picksLongestPrefix() {
        val mounts = listOf(
            "rootfs / rootfs ro 0 0",
            "/dev/a /data ext4 rw,noatime 0 0",
            "/dev/b /data/media sdcardfs rw 0 0",
        )

        assertEquals("ext4" to "rw,noatime", mountFor("/data/user/0/app/no_backup", mounts))
    }

    @Test
    fun mountFor_prefixWithoutSeparator_isNotAMatch() {
        val mounts = listOf("/dev/a /dat ext4 rw 0 0", "rootfs / rootfs ro 0 0")

        assertEquals("rootfs" to "ro", mountFor("/data/x", mounts))
    }

    @Test
    fun mountFor_optionsWithValues_keepsOnlyKeys() {
        val mounts = listOf("/dev/a /data 9p rw,aname=/home/alice/x,trans=virtio 0 0")

        assertEquals("9p" to "rw,aname,trans", mountFor("/data/x", mounts))
    }

    @Test
    fun mountFor_noMatch_returnsNull() {
        assertNull(mountFor("/data/user/0/app", listOf("/dev/a /mnt/sdcard vfat rw 0 0")))
    }

    @Test
    fun diagnostics_always_containsFsFields() {
        assertTrue(Regex("fs=(\\S+) mount=(\\S+)").containsMatchIn(databaseFiles.diagnostics(ACCOUNT_ID)))
    }

    private fun writeDatabase(): List<File> =
        listOf("", "-wal", "-shm", "-journal").map { suffix ->
            File(databaseFiles.databaseFile(ACCOUNT_ID).path + suffix).apply { writeText("data") }
        }
}
