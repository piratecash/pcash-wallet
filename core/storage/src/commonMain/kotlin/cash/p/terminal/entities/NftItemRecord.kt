package cash.p.terminal.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import io.horizontalsystems.core.entities.BlockchainType

enum class NftTrust {
    Whitelist,
    Graylist,
    None,
    Blacklist,
}

@Entity(primaryKeys = ["accountId", "blockchainType", "ownerAddress", "address"])
data class NftItemRecord(
    val accountId: String,
    val blockchainType: BlockchainType,
    val ownerAddress: String,
    val address: String,
    val position: Int,
    val name: String?,
    val description: String?,
    val realOwnerAddress: String?,
    val collectionAddress: String?,
    val collectionName: String?,
    val collectionDescription: String?,
    val trust: NftTrust,
    val verified: Boolean,
    val imageUrl: String?,
    val largeImageUrl: String?,
    @ColumnInfo(defaultValue = "0")
    val inSale: Boolean = false,
    val lottieUrl: String? = null,
)
