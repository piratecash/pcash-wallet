package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Must match Room's schema of `StackingCache`, or the post-migration validation fails.
@Suppress("ClassName")
object Migration_119_120 : Migration(119, 120) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `StackingCache` (`coin` TEXT NOT NULL, `address` TEXT NOT NULL, " +
                "`unpaid` TEXT NOT NULL, `totalIncome` TEXT NOT NULL, `nextAccrualAt` INTEGER, `nextPayoutAt` INTEGER, " +
                "`cachedBalance` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`coin`, `address`))"
        )
    }
}
