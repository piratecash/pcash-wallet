package cash.p.terminal.wallet.storage

import androidx.room.RoomRawQuery
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.Blockchain
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery

class CoinStorage(val marketDatabase: MarketDatabase) {

    private val coinDao = marketDatabase.coinDao()

    fun coin(coinUid: String): Coin? =
        coinDao.getCoin(coinUid)?.toCoin()

    fun coins(coinUids: List<String>): List<Coin> =
        coinDao.getCoins(coinUids).map { it.toCoin() }

    fun allCoins(): List<Coin> = coinDao.getAllCoins().map { it.toCoin() }

    fun fullCoins(filter: String, limit: Int): List<FullCoin> {
        val sql = """
            SELECT * FROM Coin
            WHERE ${filterWhereStatement()}
            ORDER BY ${filterOrderByStatement()}
            LIMIT ?
        """.trimIndent()

        val args = filterArgs(filter) + limit.toString()
        return coinDao.getFullCoins(rawQuery(sql, args)).map { it.fullCoin }
    }

    fun fullCoin(uid: String): FullCoin? =
        coinDao.getFullCoin(uid)?.fullCoin

    fun fullCoins(uids: List<String>): List<FullCoin> =
        coinDao.getFullCoins(uids).map { it.fullCoin }

    fun getToken(query: TokenQuery): Token? =
        getToken(query, ReferenceMatch.Exact) ?: getToken(query, ReferenceMatch.LegacySuffix)

    private fun rawQuery(sql: String, args: List<String>) = RoomRawQuery(sql) { statement ->
        args.forEachIndexed { index, arg -> statement.bindText(index + 1, arg) }
    }

    private fun getToken(query: TokenQuery, referenceMatch: ReferenceMatch): Token? {
        val (clause, args) = buildTokenQueryClause(query, referenceMatch) ?: return null
        // Order by marketCapRank to prefer canonical coin when duplicates exist
        val sql = """
            SELECT * FROM Token
            JOIN Coin ON Coin.uid = Token.coinUid
            WHERE $clause
            ORDER BY ${canonicalCoinOrderBy()}
            LIMIT 1
        """.trimIndent()
        return coinDao.getToken(rawQuery(sql, args))?.token
    }

    fun getTokens(queries: List<TokenQuery>): List<Token> {
        if (queries.isEmpty()) return listOf()

        val uniqueQueries = queries.toSet().toList()
        val tokens = getTokens(uniqueQueries, ReferenceMatch.Exact)
        val resolvedQueries = tokens.map { token -> token.tokenQuery }.toSet()
        val unresolvedQueries = uniqueQueries
            .filter { it !in resolvedQueries }
            .filter { it.tokenType.values.reference.isNotBlank() }

        if (unresolvedQueries.isEmpty()) return tokens

        return (tokens + getTokens(unresolvedQueries, ReferenceMatch.LegacySuffix))
            .distinctBy { it.tokenQuery }
    }

    private fun getTokens(
        queries: List<TokenQuery>,
        referenceMatch: ReferenceMatch
    ): List<Token> {
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        queries.forEach { query ->
            val (clause, queryArgs) = buildTokenQueryClause(query, referenceMatch)
                ?: return@forEach
            whereClauses.add(clause)
            args.addAll(queryArgs)
        }

        if (whereClauses.isEmpty()) return listOf()

        // Order by marketCapRank to prefer canonical coin when duplicates exist
        val sql = """
            SELECT * FROM Token
            JOIN Coin ON Coin.uid = Token.coinUid
            WHERE ${whereClauses.joinToString(" OR ")}
            ORDER BY ${canonicalCoinOrderBy()}
        """.trimIndent()

        // Deduplicate by tokenQuery - keep only first (canonical) token per contract
        return coinDao.getTokens(rawQuery(sql, args))
            .map { it.token }
            .distinctBy { it.tokenQuery }
    }

    fun getTokens(reference: String): List<Token> {
        val sql = "SELECT * FROM Token WHERE `Token`.`reference` LIKE ?"
        val args = listOf("%$reference")

        return coinDao.getTokens(rawQuery(sql, args)).map { it.token }
    }

    fun getTokens(blockchainType: BlockchainType, filter: String, limit: Int): List<Token> {
        val sql = """
            SELECT * FROM Token
            JOIN Coin ON `Coin`.`uid` = `Token`.`coinUid`
            WHERE
              `Token`.`blockchainUid` = ?
              AND (${filterWhereStatement()})
            ORDER BY ${filterOrderByStatement()}
            LIMIT ?
        """.trimIndent()

        val args = listOf(blockchainType.uid) + filterArgs(filter) + limit.toString()
        return coinDao.getTokens(rawQuery(sql, args)).map { it.token }
    }

    fun getBlockchain(uid: String): Blockchain? =
        coinDao.getBlockchain(uid)?.toBlockchain()

    fun getBlockchains(uids: List<String>): List<Blockchain> =
        coinDao.getBlockchains(uids).map { it.toBlockchain() }

    fun getAllBlockchains(): List<Blockchain> =
        coinDao.getAllBlockchains().map { it.toBlockchain() }

    private fun buildTokenQueryClause(
        query: TokenQuery,
        referenceMatch: ReferenceMatch
    ): Pair<String, List<String>>? {
        val (type, reference) = query.tokenType.values

        if (referenceMatch == ReferenceMatch.LegacySuffix && reference.isBlank()) return null

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        conditions.add("`Token`.`blockchainUid` = ?")
        args.add(query.blockchainType.uid)

        conditions.add("`Token`.`type` = ?")
        args.add(type)

        if (reference.isNotBlank()) {
            val referenceCondition = when (referenceMatch) {
                ReferenceMatch.Exact -> "`Token`.`reference` = ?"
                ReferenceMatch.LegacySuffix -> "`Token`.`reference` LIKE ?"
            }
            conditions.add(referenceCondition)
            args.add(referenceMatch.argument(reference))
        }

        val clause = conditions.joinToString(" AND ", "(", ")")
        return Pair(clause, args)
    }

    private fun ReferenceMatch.argument(reference: String) = when (this) {
        ReferenceMatch.Exact -> reference
        ReferenceMatch.LegacySuffix -> "%$reference"
    }

    private enum class ReferenceMatch {
        Exact,
        LegacySuffix
    }

    private fun filterWhereStatement() =
        "`Coin`.`name` LIKE ? OR `Coin`.`code` LIKE ?"

    /**
     * Orders tokens to prefer canonical coins (lower marketCapRank = more authoritative).
     * Used to ensure consistent token resolution when multiple coins map to same contract.
     */
    private fun canonicalCoinOrderBy() = """
        CASE WHEN Coin.marketCapRank IS NULL THEN 1 ELSE 0 END,
        Coin.marketCapRank ASC
    """.trimIndent()

    private fun filterOrderByStatement() = """
        priority ASC,
        CASE
            WHEN `Coin`.`code` IS NULL OR `Coin`.`code`  = '' THEN 5
            WHEN `Coin`.`name` LIKE ? THEN 1
            WHEN `Coin`.`code` LIKE ? THEN 2
            WHEN `Coin`.`name` LIKE ? THEN 3
            WHEN `Coin`.`code` LIKE ? THEN 4
            ELSE 5
        END,
        CASE
            WHEN `Coin`.`marketCapRank` IS NULL THEN 1
            ELSE 0
        END,
        `Coin`.`marketCapRank` ASC,
        `Coin`.`name` ASC
    """

    private fun filterArgs(filter: String): List<String> {
        val filterParam = "%$filter%"
        val filterStartParam = "$filter%"
        return listOf(
            // For WHERE conditions
            filterParam, filterParam,
            // For ORDER BY conditions
            filterStartParam, filterStartParam, filter, filter
        )
    }

    internal fun replaceAll(data: CoinsData) {
        marketDatabase.inTransaction {
            coinDao.deleteAllCoins()
            coinDao.deleteAllBlockchains()
            coinDao.deleteAllTokens()
            coinDao.insertCoins(data.coins)
            coinDao.insertBlockchains(data.blockchains)
            coinDao.insertTokens(data.tokens)
        }
    }

    internal fun ranks(): Map<String, Int> = coinDao.getRanks().associate { it.uid to it.marketCapRank }

    fun applyRanks(ranks: Map<String, Int>) {
        marketDatabase.inTransaction {
            coinDao.clearRanks()
            ranks.forEach { (uid, rank) -> coinDao.setRank(uid, rank) }
        }
    }
}
