package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.network.backendswap.domain.entity.BackendSwapAsset
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCreateRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal

/** The signed create-order payload; the request body is derived from the same value, so signed == sent. */
data class CreateSwapMessage(
    val provider: String,
    val type: String,
    val from: BackendSwapAsset,
    val to: BackendSwapAsset,
    val amount: BigDecimal,
    val recipient: String,
    val clientRequestId: String,
    val recipientExtraId: String? = null,
    val refundAddress: String? = null,
    val refundExtraId: String? = null,
    val fromAddress: String? = null,
    val fromExtraId: String? = null,
) {
    // Null optionals are omitted from the request; the backend verifies them as "".
    fun toCreateRequest(signature: String) = BackendSwapCreateRequest(
        provider = provider,
        type = type,
        from = from,
        to = to,
        amount = amount,
        recipient = recipient,
        recipientExtraId = recipientExtraId,
        refundAddress = refundAddress,
        refundExtraId = refundExtraId,
        fromAddress = fromAddress,
        fromExtraId = fromExtraId,
        clientRequestId = clientRequestId,
        signature = signature,
    )

    fun toTypedDataJson(): String {
        val stringFields = stringFields()
        val types = mapOf(
            "EIP712Domain" to listOf("name", "version").map { it to STRING_TYPE },
            ASSET_TYPE to listOf("coinId", "blockchain").map { it to STRING_TYPE },
            PRIMARY_TYPE to listOf("from" to ASSET_TYPE, "to" to ASSET_TYPE) +
                stringFields.map { (name, _) -> name to STRING_TYPE },
        )
        return buildJsonObject {
            putJsonObject("types") {
                types.forEach { (typeName, members) ->
                    putJsonArray(typeName) {
                        members.forEach { (name, type) -> addJsonObject { put("name", name); put("type", type) } }
                    }
                }
            }
            put("primaryType", PRIMARY_TYPE)
            putJsonObject("domain") {
                put("name", DOMAIN_NAME)
                put("version", DOMAIN_VERSION)
            }
            putJsonObject("message") {
                put("from", from.toJson())
                put("to", to.toJson())
                stringFields.forEach { (name, value) -> put(name, value) }
            }
        }.toString()
    }

    private fun stringFields() = listOf(
        "amount" to amount.toPlainString(),
        "type" to type,
        "recipient" to recipient,
        "recipientExtraId" to recipientExtraId.orEmpty(),
        "refundAddress" to refundAddress.orEmpty(),
        "refundExtraId" to refundExtraId.orEmpty(),
        "fromAddress" to fromAddress.orEmpty(),
        "fromExtraId" to fromExtraId.orEmpty(),
        "provider" to provider,
        "clientRequestId" to clientRequestId,
    )

    private fun BackendSwapAsset.toJson(): JsonObject = buildJsonObject {
        put("coinId", coinId)
        put("blockchain", blockchain)
    }

    private companion object {
        const val DOMAIN_NAME = "P.Cash Exchange"
        const val DOMAIN_VERSION = "1"
        const val PRIMARY_TYPE = "CreateExchange"
        const val ASSET_TYPE = "Asset"
        const val STRING_TYPE = "string"
    }
}
