package cash.p.terminal.feature.miniapp.data.api

import kotlinx.serialization.Serializable

@Serializable
data class EvmNonceResponseDto(
    val nonce: String,
    val expiresIn: Long
)
