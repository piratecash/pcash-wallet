package cash.p.terminal.entities

import androidx.room.Entity
import io.horizontalsystems.core.entities.BlockchainType

enum class NftUserTrust {
    None,
    Trust,
    Spam,
}

@Entity(primaryKeys = ["accountId", "blockchainType", "collectionKey"])
data class NftCollectionPrefRecord(
    val accountId: String,
    val blockchainType: BlockchainType,
    val collectionKey: String,
    val hidden: Boolean,
    val userTrust: NftUserTrust,
)
