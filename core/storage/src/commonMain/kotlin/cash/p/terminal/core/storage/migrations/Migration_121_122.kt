package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Suppress("ClassName")
object Migration_121_122 : Migration(121, 122) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS WalletConnectV2PairingMetadata")
    }
}
