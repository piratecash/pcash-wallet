package cash.p.terminal.network.yifi.domain.entity

import java.math.BigDecimal

data class YiFiOrder(
    val transactionId: String,
    val provider: String,
    val depositAddress: String,
    val extraIdDeposit: String?,
    val receiveAmount: BigDecimal,
)
