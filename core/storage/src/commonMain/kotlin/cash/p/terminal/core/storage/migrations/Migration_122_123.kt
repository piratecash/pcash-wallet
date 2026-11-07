package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Suppress("ClassName")
object Migration_122_123 : Migration(122, 123) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS NftCollectionRecord")
        connection.execSQL("DROP TABLE IF EXISTS NftAssetRecord")
        connection.execSQL("DROP TABLE IF EXISTS NftMetadataSyncRecord")
        connection.execSQL("DROP TABLE IF EXISTS NftAssetBriefMetadataRecord")
    }
}
