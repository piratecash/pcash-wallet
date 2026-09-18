package cash.p.terminal.network.yifi.data.entity

import cash.p.terminal.network.data.serializers.FlexibleBigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
internal data class YiFiPairsResponseDto(
    val tokenPairs: List<YiFiPairDto> = emptyList(),
)

@Serializable
internal data class YiFiPairDto(
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val minAmount: BigDecimal,
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val maxAmount: BigDecimal,
)
