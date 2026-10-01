package cash.p.terminal.network.backendswap.domain.entity

import java.math.BigDecimal

data class BackendSwapEstimateRequest(
    val provider: String,
    val type: String,
    val from: BackendSwapAsset,
    val to: BackendSwapAsset,
    val amount: BigDecimal,
)

data class BackendSwapEstimate(
    val amountFrom: BigDecimal,
    val amountToWithFee: BigDecimal,
    val minAmount: BigDecimal?,
    val maxAmount: BigDecimal?,
)
