package cash.p.terminal.network.yifi.data.entity

import cash.p.terminal.network.data.serializers.FlexibleBigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
internal data class YiFiSwapDto(
    val transactionId: String,
    val provider: String,
    val depositAddress: String,
    val extraIdDeposit: String? = null,
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val receiveAmount: BigDecimal,
)
