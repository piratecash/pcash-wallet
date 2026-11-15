package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Suppress("ClassName")
object Migration_126_127 : Migration(126, 127) {
    override fun migrate(connection: SQLiteConnection) {
        // Adds only what is missing, so a table that already has the columns passes unchanged.
        val existing = connection.columnNames("NftItemRecord")
        if ("inSale" !in existing) {
            connection.execSQL("ALTER TABLE `NftItemRecord` ADD COLUMN `inSale` INTEGER NOT NULL DEFAULT 0")
        }
        if ("lottieUrl" !in existing) {
            connection.execSQL("ALTER TABLE `NftItemRecord` ADD COLUMN `lottieUrl` TEXT")
        }
    }

    private fun SQLiteConnection.columnNames(table: String): Set<String> =
        prepare("PRAGMA table_info(`$table`)").use { statement ->
            buildSet {
                val nameIndex = statement.getColumnNames().indexOf("name")
                while (statement.step()) add(statement.getText(nameIndex))
            }
        }
}
