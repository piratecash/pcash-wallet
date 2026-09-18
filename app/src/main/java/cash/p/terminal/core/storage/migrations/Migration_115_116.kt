package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_115_116 : Migration(115, 116) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE AccountRecord SET type = 'mnemonic' WHERE type = 'mnemonic_bip39'")
    }
}
