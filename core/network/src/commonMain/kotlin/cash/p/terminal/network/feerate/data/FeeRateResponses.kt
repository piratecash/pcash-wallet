package cash.p.terminal.network.feerate.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MempoolRecommendedFees(
    val halfHourFee: Int,
    val minimumFee: Int,
)

@Serializable
internal data class BlockstreamEstimates(
    @SerialName("3") val threeBlocks: Double,
    @SerialName("8") val eightBlocks: Double,
)

@Serializable
internal data class BlockCypherChain(
    @SerialName("high_fee_per_kb") val highFeePerKb: Long = 10_000,
)
