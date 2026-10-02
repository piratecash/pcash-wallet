package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_116_117 : Migration(116, 117) {
    private val keptColumns = listOf(
        "date", "outgoingRecordUid", "transactionId", "status", "provider",
        "coinUidIn", "blockchainTypeIn", "amountIn", "addressIn",
        "coinUidOut", "blockchainTypeOut", "amountOut", "addressOut",
        "amountOutReal", "finishedAt", "incomingRecordUid", "accountId", "depositTransactionHash",
    )

    // RENAME COLUMN needs SQLite 3.25 (API 30), so the table is rebuilt to rename the sub-provider column.
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE SwapProviderTransaction_new (
                `date` INTEGER NOT NULL,
                `outgoingRecordUid` TEXT,
                `transactionId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `provider` TEXT NOT NULL,
                `coinUidIn` TEXT NOT NULL,
                `blockchainTypeIn` TEXT NOT NULL,
                `amountIn` TEXT NOT NULL,
                `addressIn` TEXT NOT NULL,
                `coinUidOut` TEXT NOT NULL,
                `blockchainTypeOut` TEXT NOT NULL,
                `amountOut` TEXT NOT NULL,
                `addressOut` TEXT NOT NULL,
                `amountOutReal` TEXT,
                `finishedAt` INTEGER,
                `incomingRecordUid` TEXT,
                `accountId` TEXT NOT NULL DEFAULT '',
                `depositTransactionHash` TEXT,
                `subProviderId` TEXT,
                `providerExternalId` TEXT,
                `providerWalletAddress` TEXT,
                PRIMARY KEY(`date`)
            )
            """.trimIndent()
        )
        val columns = keptColumns.joinToString(", ")
        db.execSQL(
            "INSERT INTO SwapProviderTransaction_new ($columns, subProviderId) " +
                "SELECT $columns, unstoppableSubProviderId FROM SwapProviderTransaction"
        )
        db.execSQL("DROP TABLE SwapProviderTransaction")
        db.execSQL("ALTER TABLE SwapProviderTransaction_new RENAME TO SwapProviderTransaction")
    }
}
