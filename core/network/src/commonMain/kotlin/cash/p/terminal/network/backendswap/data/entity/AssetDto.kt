package cash.p.terminal.network.backendswap.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class AssetDto(
    val coinId: String,
    val blockchain: String,
)
