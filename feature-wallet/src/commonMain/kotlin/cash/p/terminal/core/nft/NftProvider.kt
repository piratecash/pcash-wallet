package cash.p.terminal.core.nft

import cash.p.terminal.entities.NftTrust
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow

data class NftOwner(val accountId: String, val blockchainType: BlockchainType, val address: String)

sealed interface NftChange {
    data object KitAppeared : NftChange
    data object NewEvent : NftChange
}

data class NftItemRecordDraft(
    val address: String,
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
)

data class NftLiveState(
    val ownerAddress: String?,
    val isOnSale: Boolean,
    val domain: String?,
    val buttons: List<NftLinkButton> = emptyList(),
)

/** A link the NFT creator put into its metadata; both fields are untrusted. */
data class NftLinkButton(val label: String, val uri: String)

data class NftDomainDraft(
    val nftAddress: String,
    val name: String,
    val expiresAt: Long,
    val isOnSale: Boolean,
    val imageUrl: String? = null,
)

interface NftProvider {
    val blockchainType: BlockchainType
    val networkTitle: String

    fun explorerUrl(nftAddress: String): String
    fun supportsSection(wallet: Wallet): Boolean
    suspend fun ownerAddresses(wallets: List<Wallet>): Set<String>
    fun changes(address: String): Flow<NftChange>

    /** Null means the source is not available now, which is not the same as owning nothing. */
    suspend fun fetch(address: String): List<NftItemRecordDraft>?

    /** Reads the item from the network; null means the source is not available now. */
    suspend fun liveState(ownerAddress: String, nftAddress: String): NftLiveState?

    /** Domains expiring within [periodDays], read from the network; null means the source is not available now. */
    suspend fun expiringDomains(ownerAddress: String, periodDays: Int): List<NftDomainDraft>?
}
