package cash.p.terminal.network.backendswap.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class EstimateRequestDto(
    val provider: String,
    val type: String,
    val from: AssetDto,
    val to: AssetDto,
    val amount: String,
)

@Serializable
internal data class EstimateLimitsDto(
    val min: String? = null,
    val max: String? = null,
)

@Serializable
internal data class EstimateDto(
    val amountFrom: String,
    val amountToWithFee: String,
    val limits: EstimateLimitsDto? = null,
)
