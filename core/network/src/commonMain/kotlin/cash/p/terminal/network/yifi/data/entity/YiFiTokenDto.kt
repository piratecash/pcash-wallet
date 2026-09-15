package cash.p.terminal.network.yifi.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class YiFiTokensResponseDto(
    val tokens: List<YiFiTokenDto> = emptyList(),
)

@Serializable
internal data class YiFiTokenDto(
    val network: String,
    val ticker: String,
    val contractAddress: String? = null,
)
