package cash.p.terminal.network.pirate.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CoinsListDto(
    val blockchains: List<BlockchainDto>,
    val coins: List<CoinDto>,
)

@Serializable
internal data class CoinDto(
    @SerialName("coingecko_id")
    val coingeckoId: String,
    val name: String,
    val code: String,
    val priority: Int? = null,
    val tokens: List<TokenDto>,
)

@Serializable
internal data class TokenDto(
    val type: String,
    @SerialName("blockchain_uid")
    val blockchainUid: String,
    val address: String? = null,
    val decimals: Int? = null,
)

@Serializable
internal data class BlockchainDto(
    val uid: String,
    val name: String,
    val url: String? = null,
)

@Serializable
internal data class CoinsUpdatesDto(
    @SerialName("updated_at")
    val updatedAt: Long,
)
