package cash.p.terminal.network.data.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

internal class FlexibleBigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleBigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val jsonDecoder = decoder as? JsonDecoder
        val primitive = jsonDecoder?.decodeJsonElement() as? JsonPrimitive

        return BigDecimal(primitive?.content ?: decoder.decodeString())
    }
}

internal class FlexibleNullableBigDecimalSerializer : KSerializer<BigDecimal?> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleNullableBigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal?) {
        encoder.encodeString(value?.toPlainString().orEmpty())
    }

    override fun deserialize(decoder: Decoder): BigDecimal? {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive
        return when {
            primitive == null -> decoder.decodeString().takeIf { it.isNotBlank() }?.let(::BigDecimal)
            primitive is JsonNull || primitive.content.isBlank() -> null
            else -> BigDecimal(primitive.content)
        }
    }
}
