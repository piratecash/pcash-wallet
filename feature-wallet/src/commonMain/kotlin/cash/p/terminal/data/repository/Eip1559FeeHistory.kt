package cash.p.terminal.data.repository

/** Fee-history window shared by the max-spendable fee estimate and the send flow's gas price service. */
object Eip1559FeeHistory {
    const val BLOCKS_COUNT = 10L
    val REWARD_PERCENTILE = listOf(50)
    const val LAST_N_RECOMMENDED_BASE_FEES = 2
}
