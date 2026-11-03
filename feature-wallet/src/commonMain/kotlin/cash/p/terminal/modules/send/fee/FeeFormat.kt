package cash.p.terminal.modules.send.fee

import cash.p.terminal.entities.CoinValue
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.CurrencyValue
import java.math.BigDecimal

// Named apart from BaseSendViewModel's formatFee* members on purpose. feeSecondaryText is the one
// that forces it: as formatFeeSecondary it would have had a signature identical to the member, so the
// member would win overload resolution and its delegation would recurse forever. feePrimaryText takes
// one argument more than its member and would not have been ambiguous; it is renamed for symmetry.
fun feePrimaryText(feeToken: Token?, fee: BigDecimal?): String {
    if (fee == null) return "---"
    return feeToken?.let { CoinValue(it, fee).getFormattedFull() } ?: "---"
}

fun feeSecondaryText(fee: BigDecimal?, rate: CurrencyValue?): String {
    val feeValue = fee ?: return ""
    return rate?.copy(value = feeValue.times(rate.value))?.getFormattedFull() ?: ""
}
