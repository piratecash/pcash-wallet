package cash.p.terminal.network.yifi.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class YiFiChainsResponseDto(
    val chains: List<YiFiChainDto> = emptyList(),
)

@Serializable
internal data class YiFiChainDto(
    val id: String,
    val chainId: Long? = null,
    val aliases: List<String> = emptyList(),
    val nativeToken: String? = null,
)
