package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

internal fun Token.contractAddress(): String =
    when (val tokenType = type) {
        is TokenType.Eip20 -> tokenType.address
        is TokenType.Spl -> tokenType.address
        is TokenType.Jetton -> tokenType.address
        else -> ""
    }

internal val Token.isZcashShielded: Boolean
    get() = blockchainType == BlockchainType.Zcash &&
            type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded)

internal val Token.isZcashUnified: Boolean
    get() = blockchainType == BlockchainType.Zcash &&
            type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified)

// ChangeNow and Quickex settle ZEC only to transparent (t...) addresses; their APIs reject
// unified (u1...) and shielded (z...) payout addresses, so ZEC can be received through them
// only via the transparent token.
internal val Token.isZcashNonTransparent: Boolean
    get() = isZcashShielded || isZcashUnified

internal enum class SwapAssetKind { NATIVE, CONTRACT }

// Dispatch on the type, not on contractAddress(): it is empty for Asset, Trc10 and Mweb too.
internal val Token.swapAssetKind: SwapAssetKind?
    get() = when (type) {
        TokenType.Native,
        is TokenType.Derived,
        is TokenType.AddressTyped,
        is TokenType.AddressSpecTyped -> SwapAssetKind.NATIVE.takeUnless { isZcashShielded }

        is TokenType.Eip20,
        is TokenType.Spl,
        is TokenType.Jetton -> SwapAssetKind.CONTRACT

        TokenType.Mweb,
        is TokenType.Trc10,
        is TokenType.Asset,
        is TokenType.ThorchainAsset,
        is TokenType.Unsupported -> null
    }
