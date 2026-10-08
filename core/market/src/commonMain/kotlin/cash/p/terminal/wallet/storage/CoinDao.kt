package cash.p.terminal.wallet.storage

import androidx.room.*
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.extensions.isEvmLike

@Dao
internal interface CoinDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCoins(coins: List<CoinRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBlockchains(blockchains: List<BlockchainRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTokens(tokens: List<TokenRecord>)

    @Query("SELECT * FROM Coin WHERE uid = :uid LIMIT 1")
    fun getCoin(uid: String): CoinRecord?

    @Query("SELECT * FROM Coin WHERE uid IN (:uids)")
    fun getCoins(uids: List<String>): List<CoinRecord>

    @Query("SELECT * FROM Coin")
    fun getAllCoins(): List<CoinRecord>

    @Transaction
    @RawQuery
    fun getFullCoins(query: RoomRawQuery): List<FullCoinWrapper>

    @Transaction
    @Query("SELECT * FROM Coin WHERE uid = :uid LIMIT 1")
    fun getFullCoin(uid: String): FullCoinWrapper?

    @Transaction
    @Query("SELECT * FROM Coin WHERE uid IN (:uids)")
    fun getFullCoins(uids: List<String>): List<FullCoinWrapper>

    @Transaction
    @RawQuery
    fun getToken(query: RoomRawQuery): TokenWrapper?

    @Transaction
    @RawQuery
    fun getTokens(filter: RoomRawQuery): List<TokenWrapper>

    @Query("SELECT * FROM Blockchain WHERE uid = :uid LIMIT 1")
    fun getBlockchain(uid: String): BlockchainRecord?

    @Query("SELECT * FROM Blockchain WHERE uid IN (:uids)")
    fun getBlockchains(uids: List<String>): List<BlockchainRecord>

    @Query("SELECT * FROM Blockchain")
    fun getAllBlockchains(): List<BlockchainRecord>

    @Query("DELETE FROM Coin")
    fun deleteAllCoins()

    @Query("DELETE FROM Blockchain")
    fun deleteAllBlockchains()

    @Query("DELETE FROM Token")
    fun deleteAllTokens()

    @Query("SELECT COUNT(*) FROM Coin")
    fun getCoinsCount(): Int

    @Query("SELECT COUNT(*) FROM Blockchain")
    fun getBlockchainsCount(): Int

    @Query("SELECT COUNT(*) FROM Token")
    fun getTokensCount(): Int

    @Query("SELECT uid, marketCapRank FROM Coin WHERE marketCapRank IS NOT NULL")
    fun getRanks(): List<CoinRank>

    @Query("UPDATE Coin SET marketCapRank = NULL")
    fun clearRanks()

    @Query("UPDATE Coin SET marketCapRank = :rank WHERE uid = :uid")
    fun setRank(uid: String, rank: Int)

    data class FullCoinWrapper(
        @Embedded
        val coin: CoinRecord,

        @Relation(
            entity = TokenRecord::class,
            parentColumn = "uid",
            entityColumn = "coinUid"
        )
        val tokens: List<TokenRecordWrapper>
    ) {

        val fullCoin: FullCoin
            get() {
                val domainCoin = coin.toCoin()
                return FullCoin(domainCoin, tokens.map { it.token(domainCoin) })
            }

    }

    data class TokenRecordWrapper(
        @Embedded
        val tokenRecord: TokenRecord,

        @Relation(
            parentColumn = "blockchainUid",
            entityColumn = "uid"
        )
        val blockchainRecord: BlockchainRecord
    ) {

        fun token(coin: Coin): Token {
            var tokenType = if (tokenRecord.decimals != null) {
                TokenType.fromType(
                    tokenRecord.type,
                    tokenRecord.reference
                )
            } else {
                TokenType.Unsupported(
                    tokenRecord.type,
                    tokenRecord.reference
                )
            }

            if (tokenType is TokenType.Eip20 && blockchainRecord.toBlockchain().type.isEvmLike()) {
                tokenType = TokenType.Eip20(tokenType.address.lowercase())
            }

            return Token(
                coin,
                blockchainRecord.toBlockchain(),
                tokenType,
                tokenRecord.decimals ?: 0
            )
        }

    }

    data class TokenWrapper(
        @Embedded
        val tokenRecord: TokenRecord,

        @Relation(
            parentColumn = "coinUid",
            entityColumn = "uid"
        )
        val coin: CoinRecord,

        @Relation(
            parentColumn = "blockchainUid",
            entityColumn = "uid"
        )
        val blockchainRecord: BlockchainRecord,
    ) {

        val token: Token
            get() = TokenRecordWrapper(tokenRecord, blockchainRecord).token(coin.toCoin())

    }

}
