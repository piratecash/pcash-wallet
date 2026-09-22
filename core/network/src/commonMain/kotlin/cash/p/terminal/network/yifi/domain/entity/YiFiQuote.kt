package cash.p.terminal.network.yifi.domain.entity

import java.math.BigDecimal

data class YiFiQuote(
    val rateId: String?,
    val provider: String,
    val exchangerName: String,
    val estimatedOutput: BigDecimal,
    val estimatedTime: String?,
)
