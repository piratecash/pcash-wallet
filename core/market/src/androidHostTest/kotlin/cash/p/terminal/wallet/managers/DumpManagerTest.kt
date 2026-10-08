package cash.p.terminal.wallet.managers

import android.database.sqlite.SQLiteDatabase
import cash.p.terminal.wallet.storage.BlockchainRecord
import cash.p.terminal.wallet.storage.CoinRecord
import cash.p.terminal.wallet.storage.TokenRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DumpManagerTest {

    private val tablesCreation =
        "CREATE TABLE IF NOT EXISTS `Blockchain` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`eip3091url` TEXT, PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `Coin` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, " +
                "`marketCapRank` INTEGER, `priority` INTEGER, PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `Token` (`coinUid` TEXT NOT NULL, `blockchainUid` TEXT NOT " +
                "NULL, `type` TEXT NOT NULL, `decimals` INTEGER, `reference` TEXT NOT NULL, PRIMARY KEY(`coinUid`, " +
                "`blockchainUid`, `type`, `reference`), " +
                "FOREIGN KEY(`coinUid`) REFERENCES `Coin`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`blockchainUid`) REFERENCES `Blockchain`(`uid`) ON UPDATE NO ACTION ON DELETE " +
                "CASCADE )\n"

    private val expectedDump = tablesCreation +
            "INSERT OR REPLACE INTO Blockchain VALUES('chain''s-uid','Chain''s Name'," +
            "'https://etherscan.io/tx''s'),('no-url-chain','No Url Chain',null);\n" +
            "INSERT OR REPLACE INTO Coin VALUES('coin''s-uid', 'Coin''s Name', 'cus', 5, 3)," +
            "('null-coin', 'Null Coin', 'nul', null, null);\n" +
            "INSERT OR REPLACE INTO Token VALUES('token''s-coin','token''s-chain','eip20''s-type',18," +
            "'0xAbc''s123'),('coin2','chain2','native',null,'');\n"

    @Test
    fun getInitialDump_quotedAndNullFields_escapesQuotesAndFormatsNulls() {
        val blockchainWithQuotesAndUrl = BlockchainRecord(
            uid = "chain's-uid",
            name = "Chain's Name",
            eip3091url = "https://etherscan.io/tx's"
        )
        val blockchainWithNullUrl = BlockchainRecord(
            uid = "no-url-chain",
            name = "No Url Chain",
            eip3091url = null
        )
        val coinWithQuotesAndValues = CoinRecord(
            uid = "coin's-uid",
            name = "Coin's Name",
            code = "cus",
            marketCapRank = 5,
            priority = 3
        )
        val coinWithNullFields = CoinRecord(
            uid = "null-coin",
            name = "Null Coin",
            code = "nul",
            marketCapRank = null,
            priority = null
        )
        val tokenWithQuotesEverywhere = TokenRecord(
            coinUid = "token's-coin",
            blockchainUid = "token's-chain",
            type = "eip20's-type",
            decimals = 18,
            reference = "0xAbc's123"
        )
        val tokenWithNullDecimalsAndEmptyReference = TokenRecord(
            coinUid = "coin2",
            blockchainUid = "chain2",
            type = "native",
            decimals = null,
            reference = ""
        )

        val dump = DumpManager.getInitialDump(
            blockchains = listOf(blockchainWithQuotesAndUrl, blockchainWithNullUrl),
            coins = listOf(coinWithQuotesAndValues, coinWithNullFields),
            tokens = listOf(tokenWithQuotesEverywhere, tokenWithNullDecimalsAndEmptyReference)
        )

        assertEquals(expectedDump, dump)
    }

    @Test
    fun getInitialDump_emptyLists_returnsOnlyTablesCreation() {
        val dump = DumpManager.getInitialDump(emptyList(), emptyList(), emptyList())

        assertEquals(tablesCreation, dump)
    }

    @Test
    fun getInitialDump_moreThan500Coins_chunksIntoCeilNBy500StatementsAndExecutesToExactRowCount() {
        val rowCount = 501
        val coins = (1..rowCount).map { i -> CoinRecord(uid = "coin-$i", name = "Coin $i", code = "C$i") }

        val dump = DumpManager.getInitialDump(blockchains = emptyList(), coins = coins, tokens = emptyList())

        val coinInsertStatements = dump.lines().filter { it.startsWith("INSERT OR REPLACE INTO Coin VALUES") }
        assertEquals(ceil(rowCount / 500.0).toInt(), coinInsertStatements.size)

        val database = SQLiteDatabase.create(null)
        try {
            dump.lines().filter { it.isNotBlank() }.forEach { statement -> database.execSQL(statement) }
            database.rawQuery("SELECT COUNT(*) FROM Coin", null).use { cursor ->
                cursor.moveToFirst()
                assertEquals(rowCount, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }
}
