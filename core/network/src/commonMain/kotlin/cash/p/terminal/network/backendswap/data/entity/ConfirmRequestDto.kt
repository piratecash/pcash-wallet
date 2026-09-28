package cash.p.terminal.network.backendswap.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class ConfirmRequestDto(
    val txHash: String,
    val amount: String,
    val address: String,
    val addressExtraId: String? = null,
    val currency: AssetDto,
)
