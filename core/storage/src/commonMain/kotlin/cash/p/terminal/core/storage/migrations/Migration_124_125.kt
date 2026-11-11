package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Suppress("ClassName")
object Migration_124_125 : Migration(124, 125) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `NftItemRecord` (" +
                "`accountId` TEXT NOT NULL, " +
                "`blockchainType` TEXT NOT NULL, " +
                "`ownerAddress` TEXT NOT NULL, " +
                "`address` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`name` TEXT, " +
                "`description` TEXT, " +
                "`realOwnerAddress` TEXT, " +
                "`collectionAddress` TEXT, " +
                "`collectionName` TEXT, " +
                "`collectionDescription` TEXT, " +
                "`trust` TEXT NOT NULL, " +
                "`verified` INTEGER NOT NULL, " +
                "`imageUrl` TEXT, " +
                "`largeImageUrl` TEXT, " +
                "PRIMARY KEY(`accountId`, `blockchainType`, `ownerAddress`, `address`))"
        )
    }
}
