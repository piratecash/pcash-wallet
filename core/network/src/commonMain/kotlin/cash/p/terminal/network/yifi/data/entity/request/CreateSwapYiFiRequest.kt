package cash.p.terminal.network.yifi.data.entity.request

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

// The server rejects unknown fields, so nullable fields stay at their defaults to be omitted.
@Serializable
data class CreateSwapYiFiRequest(
    val provider: String,
    val fromToken: String,
    val fromNetwork: String,
    val toToken: String,
    val toNetwork: String,
    @Serializable(with = JsonNumberBigDecimalSerializer::class)
    val amount: BigDecimal,
    val receiveAddress: String,
    val rateId: String? = null,
    val refundAddress: String? = null,
    val engine: String? = null, // set by YiFiApi
    val refCode: String? = null, // set by YiFiApi
) {
    internal fun withPartnerFields(engine: String, refCode: String): CreateSwapYiFiRequest {
        return copy(engine = engine, refCode = refCode)
    }
}

/** Encodes as an unquoted JSON number without going through Double. */
@OptIn(ExperimentalSerializationApi::class)
internal object JsonNumberBigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("JsonNumberBigDecimal", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)
}
