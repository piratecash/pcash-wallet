package cash.p.terminal.network.backendswap.domain.entity

import java.math.BigDecimal

data class BackendSwapCreateRequest(
    val provider: String,
    val type: String,
    val from: BackendSwapAsset,
    val to: BackendSwapAsset,
    val amount: BigDecimal,
    val recipient: String,
    val recipientExtraId: String? = null,
    val refundAddress: String? = null,
    val refundExtraId: String? = null,
    val fromAddress: String? = null,
    val fromExtraId: String? = null,
    val clientRequestId: String,
    val signature: String,
)

data class BackendSwapCreatedOrder(
    val id: String,
    val externalId: String?,
    val payinAddress: String?,
    val payinExtraId: String?,
    val amountToExpected: BigDecimal?,
    val payinExpiresAt: Long?,
    val status: String,
)
