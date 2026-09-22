package cash.p.terminal.network.yifi.data.entity

import cash.p.terminal.network.data.serializers.FlexibleBigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
internal data class YiFiQuoteResponseDto(
    val bestQuote: List<YiFiQuoteDto> = emptyList(),
)

@Serializable
internal data class YiFiQuoteDto(
    val rateId: String? = null,
    val provider: String,
    val exchangerName: String,
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val estimatedOutput: BigDecimal,
    val estimatedTime: String? = null,
)
