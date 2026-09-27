package cash.p.terminal.modules.multiswap

import cash.p.terminal.entities.PendingMultiSwap
import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType

data class SwapSide(
    val tokenQueryId: String?,
    val coinUid: String,
    val blockchainTypeUid: String,
)

val PendingMultiSwap.sideIn: SwapSide
    get() = SwapSide(tokenQueryIdIn, coinUidIn, blockchainTypeIn)

val PendingMultiSwap.sideIntermediate: SwapSide
    get() = SwapSide(tokenQueryIdIntermediate, coinUidIntermediate, blockchainTypeIntermediate)

val PendingMultiSwap.sideOut: SwapSide
    get() = SwapSide(tokenQueryIdOut, coinUidOut, blockchainTypeOut)

/** PayCore RUB is not a real token, so its side carries no token identity. */
val Token.swapSideTokenQueryId: String?
    get() = tokenQuery.id.takeUnless { PayCoreAssets.isRub(this) }

// Sides saved before tokenQueryId existed fall back to the coin label + chain.
fun List<Wallet>.findBySwapSide(side: SwapSide): Wallet? {
    val tokenQueryId = side.tokenQueryId
    if (tokenQueryId != null) return firstOrNull { it.token.tokenQuery.id == tokenQueryId }

    val blockchainType = BlockchainType.fromUid(side.blockchainTypeUid)
    return firstOrNull { it.coin.uid == side.coinUid && it.token.blockchainType == blockchainType }
}
