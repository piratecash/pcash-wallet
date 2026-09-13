package cash.p.terminal.feature.miniapp.data.api

import kotlinx.serialization.Serializable

@Serializable
data class PCashWalletRequestDto(
    val walletAddress: String,
    val premiumAddress: String,
    val premiumSignature: String,
    val pirate: String,
    val cosa: String,
    val uniqueCode: String?,
    val apiVersion: Int
)

@Serializable
data class PCashWalletResponseDto(
    val uniqueCode: String? = null
)
