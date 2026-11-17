package cash.p.terminal.data.repository

import io.horizontalsystems.ethereumkit.core.eip1559.FeeHistory
import io.horizontalsystems.ethereumkit.models.GasPrice

/** Fee-history window and recommended price shared by the max-spendable fee estimate and the send flow. */
object Eip1559FeeHistory {
    const val BLOCKS_COUNT = 10L
    val REWARD_PERCENTILE = listOf(50)
    const val LAST_N_RECOMMENDED_BASE_FEES = 2

    /** Headroom for baseFee growth between the fee estimate and the broadcast. */
    private const val GAS_PRICE_HEADROOM_PERCENT = 125

    fun recommendedBaseFee(feeHistory: FeeHistory, minBaseFee: Long? = null): Long =
        maxOf(feeHistory.baseFeePerGas.takeLast(LAST_N_RECOMMENDED_BASE_FEES).max(), minBaseFee ?: 0L)

    fun recommendedPriorityFee(feeHistory: FeeHistory, minPriorityFee: Long? = null): Long {
        val priorityFees = feeHistory.reward.mapNotNull { it.firstOrNull() }
        val average = if (priorityFees.isNotEmpty()) priorityFees.sum() / priorityFees.size else 0L
        return maxOf(average, minPriorityFee ?: 0L)
    }

    fun recommendedGasPrice(baseFee: Long, priorityFee: Long) = GasPrice.Eip1559(
        (baseFee + priorityFee) * GAS_PRICE_HEADROOM_PERCENT / 100,
        priorityFee
    )

    fun recommendedGasPrice(feeHistory: FeeHistory): GasPrice.Eip1559 =
        recommendedGasPrice(recommendedBaseFee(feeHistory), recommendedPriorityFee(feeHistory))
}
