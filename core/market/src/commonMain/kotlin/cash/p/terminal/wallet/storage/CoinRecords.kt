package cash.p.terminal.wallet.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import cash.p.terminal.wallet.entities.Coin
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Market-database rows, kept separate from the domain models (`Coin`, `Blockchain`, `Token`).
 * `uid` is the coingecko_id from the V2 coin list; the server no longer sends `image`, so the
 * domain `Coin` mapped from a record always has `image = null` (own tokens set it separately).
 */
@Entity(tableName = "Coin")
internal data class CoinRecord(
    @PrimaryKey
    val uid: String,
    val name: String,
    val code: String,
    val marketCapRank: Int? = null,
    val priority: Int? = null,
)

internal fun CoinRecord.toCoin() = Coin(
    uid = uid,
    name = name,
    code = code,
    marketCapRank = marketCapRank,
    image = null,
    priority = priority,
)

@Entity(tableName = "Blockchain")
internal data class BlockchainRecord(
    @PrimaryKey
    val uid: String,
    val name: String,
    val eip3091url: String?,
)

internal fun BlockchainRecord.toBlockchain() = Blockchain(
    BlockchainType.fromUid(uid),
    name,
    eip3091url,
)

@Entity(
    tableName = "Token",
    primaryKeys = ["coinUid", "blockchainUid", "type", "reference"],
    foreignKeys = [
        ForeignKey(
            entity = CoinRecord::class,
            parentColumns = ["uid"],
            childColumns = ["coinUid"],
            onDelete = CASCADE
        ),
        ForeignKey(
            entity = BlockchainRecord::class,
            parentColumns = ["uid"],
            childColumns = ["blockchainUid"],
            onDelete = CASCADE
        ),
    ],
    indices = [
        Index(value = ["blockchainUid", "type", "reference"]),
    ]
)
internal data class TokenRecord(
    val coinUid: String,
    val blockchainUid: String,
    val type: String,
    val decimals: Int?,
    val reference: String,
)

internal data class CoinRank(val uid: String, val marketCapRank: Int)

internal data class CoinsData(
    val coins: List<CoinRecord>,
    val blockchains: List<BlockchainRecord>,
    val tokens: List<TokenRecord>,
)
