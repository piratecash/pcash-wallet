package cash.p.terminal.network.backendswap.data.entity

import kotlinx.serialization.Serializable

// The server treats an absent optional field as "" for EIP-712 signing, so omitting a null
// signed-but-empty field here (default kotlinx.serialization behaviour) matches what was signed.
@Serializable
internal data class CreateRequestDto(
    val provider: String,
    val type: String,
    val from: AssetDto,
    val to: AssetDto,
    val amount: String,
    val recipient: String,
    val recipientExtraId: String? = null,
    val refundAddress: String? = null,
    val refundExtraId: String? = null,
    val fromAddress: String? = null,
    val fromExtraId: String? = null,
    val clientRequestId: String,
    val signature: String,
)

@Serializable
internal data class CreatedSwapDto(
    val id: String,
    val externalId: String? = null,
    val payinAddress: String? = null,
    val payinExtraId: String? = null,
    val amountToExpected: String? = null,
    val payinExpiresAt: Long? = null,
    val status: String,
)
