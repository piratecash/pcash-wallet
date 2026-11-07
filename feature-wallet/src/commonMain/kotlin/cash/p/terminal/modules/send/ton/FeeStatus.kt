package cash.p.terminal.modules.send.ton

import java.math.BigDecimal

sealed interface FeeStatus {
    object NoEnoughBalance : FeeStatus
    // Monero shares this type; for it the required balance is just the fee.
    data class Success(val fee: BigDecimal, val requiredTon: BigDecimal = fee) : FeeStatus
}
