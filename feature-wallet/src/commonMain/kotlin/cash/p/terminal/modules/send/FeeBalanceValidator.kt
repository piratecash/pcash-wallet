package cash.p.terminal.modules.send

import cash.p.terminal.core.isNative
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

fun hasInsufficientFeeTokenBalance(
    token: Token,
    fee: BigDecimal?,
    feeTokenBalance: BigDecimal?,
): Boolean {
    if (token.type.isNative) return false
    val currentFee = fee ?: return false
    return currentFee > (feeTokenBalance ?: BigDecimal.ZERO)
}

enum class TokenFeeStatus { Sufficient, CertainlyShort, MaybeShort }

/** A fetched balance below the fee itself cannot pay (jetton senders leave dust, so not just zero). */
fun tokenFeeStatus(
    transferAlwaysCostsNative: Boolean,
    nativeBalance: BigDecimal?,
    nativeBalanceLoaded: Boolean,
    fee: BigDecimal?,
    requiredBalance: BigDecimal?,
): TokenFeeStatus = when {
    nativeBalance == null -> TokenFeeStatus.Sufficient
    transferAlwaysCostsNative && nativeBalanceLoaded && nativeBalance.cannotPay(fee) -> TokenFeeStatus.CertainlyShort
    requiredBalance != null && nativeBalance < requiredBalance -> TokenFeeStatus.MaybeShort
    else -> TokenFeeStatus.Sufficient
}

private fun BigDecimal.cannotPay(fee: BigDecimal?): Boolean = signum() <= 0 || (fee != null && this < fee)
