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

    private companion object {
        const val DATA_DIR_NAME = "zcash"

        /** ECC's own constant, typo included. */
        const val ECC_NO_BACKUP_DIR_NAME = "co.electricoin.zcash"
        const val PRISTINE_SUFFIX = ".pristine"

        /** The mark first: `all` stops at the first failure, so an undeletable mark keeps the database. */
        val DB_SUFFIXES = listOf(PRISTINE_SUFFIX, "", "-wal", "-shm", "-journal")
    }
}
