package cash.p.terminal.network.backendswap.domain.entity

import java.math.BigDecimal

data class BackendSwapConfirmRequest(
    val txHash: String,
    val amount: BigDecimal,
    val address: String,
    val addressExtraId: String? = null,
    val currency: BackendSwapAsset,
)
