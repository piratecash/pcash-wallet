package cash.p.terminal.core.adapters.zcash.session

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import cash.p.terminal.core.tryOrNull
import java.io.File

/** The on-disk layout of the Zcash wallet: one owner for the paths the opener and the eraser share. */
class ZcashDatabaseFiles(context: Context) {

    /** Also holds the Sapling parameters and the Tor state, so it must survive an app restart. */
    val dataDir: File = File(context.noBackupFilesDir, DATA_DIR_NAME)

    /** Where the ECC SDK left the Sapling parameters; reusing them saves a ~50 MB download. */
    val legacyDir: File = File(context.noBackupFilesDir, ECC_NO_BACKUP_DIR_NAME)

    fun databaseFile(accountId: String): File = File(dataDir, "wallet_$accountId.sqlite3")

    /**
     * A seed born in this install whose database was never erased: the mark dies with the database.
     * A mark that cannot be written is simply absent — the account then owes today's deep walk.
     */
    fun markPristine(accountId: String) {
        tryOrNull {
            dataDir.mkdirs()
            pristineMark(accountId).createNewFile()
        }
    }

    fun isPristine(accountId: String): Boolean = pristineMark(accountId).exists()

    private fun pristineMark(accountId: String) = File(databaseFile(accountId).path + PRISTINE_SUFFIX)

    /** True once nothing is left on disk, so an already-clean account counts as deleted. */
    fun delete(accountId: String): Boolean {
        val path = databaseFile(accountId).path
        return DB_SUFFIXES.map { File(path + it) }.all { it.delete() || !it.exists() }
    }

    /** One `db-fs:` line of filesystem facts for the App Log; never opens the database files and never throws. */
    fun diagnostics(accountId: String): String {
        val dbPath = databaseFile(accountId).path
        val journal = File("$dbPath-journal")
        val db = databaseFile(accountId)
        return listOf(
            "db-fs:", "dir=${state(dataDir)}", statvfsFields(),
            "db=${state(db)}", "size=${db.length()}", "uid=${uidField(db)}",
            "journal=${state(journal)}", "size=${journal.length()}", "wal=${state(File("$dbPath-wal"))}",
            "dbAccess=${accessProbe(db)}", "journalAccess=${accessProbe(journal)}",
            "probeCreate=${createProbe(accountId)}", fdFields(), mountFields(),
        ).joinToString(" ")
    }

    private fun state(file: File) = if (file.exists()) "exists" else "absent"

    private fun statvfsFields(): String {
        val stat = tryOrNull { Os.statvfs((if (dataDir.exists()) dataDir else dataDir.parentFile).path) }
        val readOnly = stat?.let { (it.f_flag and OsConstants.ST_RDONLY.toLong()) != 0L }
        return "ro=${readOnly ?: NA} free=${stat?.let { it.f_bavail * it.f_frsize } ?: NA} " +
            "inodes=${stat?.f_favail ?: NA}"
    }

    private fun uidField(file: File): String = tryOrNull {
        val uid = Os.stat(file.path).st_uid
        if (uid == Os.getuid()) "own" else uid.toString()
    } ?: NA

    private fun accessProbe(file: File): String =
        if (file.exists()) errnoOrOk { Os.access(file.path, OsConstants.R_OK or OsConstants.W_OK) } else NA

    private fun createProbe(accountId: String): String = errnoOrOk {
        val path = File(dataDir, ".probe-$accountId").path
        Os.close(Os.open(path, OsConstants.O_RDWR or OsConstants.O_CREAT, PROBE_FILE_MODE))
        Os.remove(path)
    }

    /** The errno name is the data here, so it must not collapse into n/a. */
    private fun errnoOrOk(probe: () -> Unit): String = tryOrNull {
        try {
            probe()
            "ok"
        } catch (e: ErrnoException) {
            OsConstants.errnoName(e.errno)
        }
    } ?: NA

    private fun mountFields(): String {
        val mount = tryOrNull { mountFor(dataDir.path, File("/proc/mounts").readLines()) }
        return "fs=${mount?.first ?: NA} mount=${mount?.second ?: NA}"
    }

    private fun fdFields(): String {
        val open = tryOrNull { File("/proc/self/fd").list()?.size }
        val limit = tryOrNull {
            File("/proc/self/limits").readLines().first { it.startsWith("Max open files") }
                .split(Regex("\\s+"))[3]
        }
        return "fds=${if (open != null && limit != null) "$open/$limit" else NA}"
    }

    private companion object {
        const val NA = "n/a"
        const val PROBE_FILE_MODE = 0b110_000_000
        const val DATA_DIR_NAME = "zcash"

        /** ECC's own constant, typo included. */
        const val ECC_NO_BACKUP_DIR_NAME = "co.electricoin.zcash"
        const val PRISTINE_SUFFIX = ".pristine"

        /** The mark first: `all` stops at the first failure, so an undeletable mark keeps the database. */
        val DB_SUFFIXES = listOf(PRISTINE_SUFFIX, "", "-wal", "-shm", "-journal")
    }
}

/** Type and option keys only (values may hold host paths) of the longest mountpoint containing [path]. */
internal fun mountFor(path: String, mounts: List<String>): Pair<String, String>? =
    mounts.map { it.split(" ") }
        .filter { it.size >= 4 && (it[1] == "/" || path == it[1] || path.startsWith(it[1] + "/")) }
        .maxByOrNull { it[1].length }
        ?.let { it[2] to it[3].split(",").joinToString(",") { option -> option.substringBefore('=') } }
