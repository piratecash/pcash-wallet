package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_119_120 : Migration(119, 120) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_STACKING_CACHE_TABLE)
    }
}
