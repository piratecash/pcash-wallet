package cash.p.terminal.core.adapters.zcash.session

import android.content.Context
import cash.p.terminal.core.tryOrNull
import java.io.File

/** The on-disk layout of the Zcash wallet: one owner for the paths the opener and the eraser share. */
class ZcashDatabaseFiles(context: Context) {

    /** Also holds the Sapling parameters and the Tor state, so it must survive an app restart. */
    val dataDir: File = File(context.noBackupFilesDir, DATA_DIR_NAME)

    /** Where the ECC SDK left the Sapling parameters; reusing them saves a ~50 MB download. */
    val legacyDir: File = File(context.noBackupFilesDir, ECC_NO_BACKUP_DIR_NAME)

    fun databaseFile(accountId: String): File = File(dataDir, "$DB_PREFIX$accountId$DB_SUFFIX")

    /** Inverse of [databaseFile]; null for a path this wallet does not own. */
    fun accountIdOf(path: String): String? {
        val file = File(path)
        if (file.parentFile != dataDir) return null
        return file.name.takeIf { it.startsWith(DB_PREFIX) && it.endsWith(DB_SUFFIX) }
            ?.removeSurrounding(DB_PREFIX, DB_SUFFIX)?.takeIf(String::isNotEmpty)
    }

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
        val db = databaseFile(accountId)
        return "db-fs: dir=${state(dataDir)} db=${state(db)} size=${db.length()}"
    }

    private fun state(file: File) = if (file.exists()) "exists" else "absent"

    private companion object {
        const val DATA_DIR_NAME = "zcash"
        const val DB_PREFIX = "wallet_"
        const val DB_SUFFIX = ".sqlite3"

        /** ECC's own constant, typo included. */
        const val ECC_NO_BACKUP_DIR_NAME = "co.electricoin.zcash"
        const val PRISTINE_SUFFIX = ".pristine"

        /** The mark first: `all` stops at the first failure, so an undeletable mark keeps the database. */
        val DB_SUFFIXES = listOf(PRISTINE_SUFFIX, "", "-wal", "-shm", "-journal")
    }
}
