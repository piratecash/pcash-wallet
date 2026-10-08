package cash.p.terminal.wallet.managers

import cash.p.terminal.wallet.storage.BlockchainRecord
import cash.p.terminal.wallet.storage.CoinRecord
import cash.p.terminal.wallet.storage.TokenRecord

object DumpManager {

    // Loading thousands of single-row statements blocks MarketDatabase.onCreate on the main
    // thread; batching into multi-row INSERTs cuts the statement count (and load time) by ~500x.
    private const val CHUNK_SIZE = 500

    private const val tablesCreation =
        "CREATE TABLE IF NOT EXISTS `Blockchain` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`eip3091url` TEXT, PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `Coin` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT " +
                "NULL, `marketCapRank` INTEGER, `priority` INTEGER, PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `Token` (`coinUid` TEXT NOT NULL, `blockchainUid` TEXT NOT " +
                "NULL, `type` TEXT NOT NULL, `decimals` INTEGER, `reference` TEXT NOT NULL, PRIMARY KEY(`coinUid`, " +
                "`blockchainUid`, `type`, `reference`), FOREIGN KEY(`coinUid`) REFERENCES `Coin`(`uid`) ON UPDATE " +
                "NO ACTION ON DELETE CASCADE , FOREIGN KEY(`blockchainUid`) REFERENCES `Blockchain`(`uid`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )\n"

    internal fun getInitialDump(
        blockchains: List<BlockchainRecord>,
        coins: List<CoinRecord>,
        tokens: List<TokenRecord>
    ): String {
        val insertQueries = StringBuilder()
        insertQueries.append(tablesCreation)

        // Step 1: Insert Blockchains (no dependencies)
        appendChunkedInserts(insertQueries, "Blockchain", blockchains) { blockchain ->
            val eipUrl = blockchain.eip3091url?.let { sqlEscape(it) } ?: "null"
            "(${sqlEscape(blockchain.uid)},${sqlEscape(blockchain.name)},$eipUrl)"
        }

        // Step 2: Insert Coins (depend on nothing)
        appendChunkedInserts(insertQueries, "Coin", coins) { coin ->
            val uid = sqlEscape(coin.uid)
            val name = sqlEscape(coin.name)
            val code = sqlEscape(coin.code)
            val priority = coin.priority ?: "null"
            val rank = coin.marketCapRank?.toString() ?: "null"
            "($uid, $name, $code, $rank, $priority)"
        }

        // Step 3: Insert Tokens (depend on Coins and Blockchains)
        appendChunkedInserts(insertQueries, "Token", tokens) { token ->
            val reference = sqlEscape(token.reference)
            "(${sqlEscape(token.coinUid)},${sqlEscape(token.blockchainUid)}," +
                    "${sqlEscape(token.type)},${token.decimals},$reference)"
        }

        return insertQueries.toString()
    }

    private fun <T> appendChunkedInserts(
        target: StringBuilder,
        table: String,
        records: List<T>,
        tupleOf: (T) -> String
    ) {
        records.chunked(CHUNK_SIZE).forEach { chunk ->
            target.append("INSERT OR REPLACE INTO $table VALUES")
                .append(chunk.joinToString(",", transform = tupleOf))
                .append(";\n")
        }
    }

    private fun sqlEscape(value: String) = "'" + value.replace("'", "''") + "'"
}
