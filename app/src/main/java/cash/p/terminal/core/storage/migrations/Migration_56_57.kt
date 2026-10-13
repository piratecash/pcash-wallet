package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.IPinSettingsStorage

object Migration_56_57 : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE AccountRecord ADD `level` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `Pin` (`level` INTEGER NOT NULL, `passcode` TEXT, PRIMARY KEY(`level`))")

        getKoinInstance<IPinSettingsStorage>().pin?.let {
            db.execSQL("INSERT INTO `Pin` VALUES(0, ?)", arrayOf(it))
        }
    }
}
