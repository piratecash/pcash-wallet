package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Suppress("ClassName")
object Migration_123_124 : Migration(123, 124) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE LogEntry ADD COLUMN userLevel INTEGER NOT NULL DEFAULT 0")
    }
}
