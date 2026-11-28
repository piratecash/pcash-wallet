package cash.p.terminal.core

import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

class FeeTokenBalance(val amount: BigDecimal, val fetched: Boolean)

fun IAdapterManager.getFeeTokenBalance(feeToken: Token, currentToken: Token): BigDecimal? =
    feeTokenBalance(feeToken, currentToken)?.amount

/** [FeeTokenBalance.fetched] comes from the source of the amount, so it never vouches for another one's default zero. */
fun IAdapterManager.feeTokenBalance(feeToken: Token, currentToken: Token): FeeTokenBalance? {
    // The flag is read before the amount: an amount read after a "fetched" is never the default zero.
    val nativeFetched = (getAdapterForToken<IBalanceAdapter>(feeToken) as? IBalanceFetchState)?.balanceFetched == true
    getAdjustedBalanceDataForToken(feeToken)?.let { return FeeTokenBalance(it.available, nativeFetched) }
    val provider = getAdapterForToken<IBalanceAdapter>(currentToken) as? INativeBalanceProvider ?: return null
    val loaded = provider.nativeBalanceLoaded
    return FeeTokenBalance(provider.nativeBalanceData.available, loaded)
}
