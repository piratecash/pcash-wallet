package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Suppress("ClassName")
object Migration_120_121 : Migration(120, 121) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE SwapProviderTransaction ADD COLUMN receivedAt INTEGER")
    }
}
