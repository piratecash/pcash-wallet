package cash.p.terminal.network.backendswap.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class SwapDto(
    val id: String,
    val status: String,
    val amountToActual: String? = null,
)
