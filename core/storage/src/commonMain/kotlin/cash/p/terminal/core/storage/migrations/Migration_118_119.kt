package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Released builds stored a backend zero as the actual amount, which blocks incoming-payout matching.
@Suppress("ClassName")
object Migration_118_119 : Migration(118, 119) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "UPDATE SwapProviderTransaction SET amountOutReal = NULL " +
                "WHERE amountOutReal IS NOT NULL AND CAST(amountOutReal AS REAL) <= 0"
        )
    }
}
