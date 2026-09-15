package cash.p.terminal.network.yifi.domain.entity

import java.math.BigDecimal

data class YiFiPair(
    val minAmount: BigDecimal,
    /** Zero means there is no maximum. */
    val maxAmount: BigDecimal,
)
