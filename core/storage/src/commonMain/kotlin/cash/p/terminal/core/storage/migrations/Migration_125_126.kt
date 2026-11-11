package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Suppress("ClassName")
object Migration_125_126 : Migration(125, 126) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `NftCollectionPrefRecord` (" +
                "`accountId` TEXT NOT NULL, " +
                "`blockchainType` TEXT NOT NULL, " +
                "`collectionKey` TEXT NOT NULL, " +
                "`hidden` INTEGER NOT NULL, " +
                "`userTrust` TEXT NOT NULL, " +
                "PRIMARY KEY(`accountId`, `blockchainType`, `collectionKey`))"
        )
    }
}
