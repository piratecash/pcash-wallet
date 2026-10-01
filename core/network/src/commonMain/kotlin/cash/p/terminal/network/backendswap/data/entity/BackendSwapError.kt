package cash.p.terminal.network.backendswap.data.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class BackendSwapError(
    val statusCode: Int,
    val code: String? = null,
    override val message: String? = null,
    val minAmount: String? = null,
    val maxAmount: String? = null,
    val limitType: String? = null,
) : Throwable() {
    override fun toString(): String =
        "BackendSwapError(statusCode=$statusCode, code=$code, message=$message, minAmount=$minAmount, " +
            "maxAmount=$maxAmount, limitType=$limitType)"
}

// Covers all three documented error shapes at once (message-only, {errors}, {error}); a 422
// AMOUNT_LIMITS_ERROR response carries both `errors` and `error` together.
@Serializable
internal data class BackendSwapErrorResponseDto(
    val message: String? = null,
    val errors: Map<String, JsonElement>? = null,
    val error: BackendSwapErrorPayloadDto? = null,
)

@Serializable
internal data class BackendSwapErrorPayloadDto(
    val code: String? = null,
    val message: String? = null,
    val details: BackendSwapErrorDetailsDto? = null,
)

@Serializable
internal data class BackendSwapErrorDetailsDto(
    val limits: EstimateLimitsDto? = null,
    val limitType: String? = null,
)

internal fun BackendSwapErrorResponseDto?.firstValidationMessage(): String? =
    this?.errors?.values?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
