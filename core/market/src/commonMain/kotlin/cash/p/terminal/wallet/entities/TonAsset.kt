package cash.p.terminal.wallet.entities

import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType

sealed interface TonAsset {
    val version: TonWalletVersion

    data class Native(override val version: TonWalletVersion) : TonAsset

    data class Jetton(val masterAddress: String, override val version: TonWalletVersion) : TonAsset
}

val TokenQuery.tonAsset: TonAsset?
    get() = if (blockchainType == BlockchainType.Ton) tokenType.tonAsset else null

val Token.tonAsset: TonAsset?
    get() = tokenQuery.tonAsset

private val TokenType.tonAsset: TonAsset?
    get() = when (this) {
        TokenType.Native -> TonAsset.Native(TonWalletVersion.V4R2)
        TokenType.TonW5Native -> TonAsset.Native(TonWalletVersion.W5)
        is TokenType.Jetton -> TonAsset.Jetton(address, TonWalletVersion.V4R2)
        is TokenType.TonW5Jetton -> TonAsset.Jetton(address, TonWalletVersion.W5)
        TokenType.Mweb,
        is TokenType.Derived,
        is TokenType.AddressTyped,
        is TokenType.AddressSpecTyped,
        is TokenType.Eip20,
        is TokenType.Trc10,
        is TokenType.Spl,
        is TokenType.Asset,
        is TokenType.ThorchainAsset,
        is TokenType.Unsupported -> null
    }

val TonAsset.tokenType: TokenType
    get() = when (this) {
        is TonAsset.Native -> when (version) {
            TonWalletVersion.V4R2 -> TokenType.Native
            TonWalletVersion.W5 -> TokenType.TonW5Native
        }

        is TonAsset.Jetton -> when (version) {
            TonWalletVersion.V4R2 -> TokenType.Jetton(masterAddress)
            TonWalletVersion.W5 -> TokenType.TonW5Jetton(masterAddress)
        }
    }

val TonAsset.tokenQuery: TokenQuery
    get() = TokenQuery(BlockchainType.Ton, tokenType)

fun TonAsset.withVersion(version: TonWalletVersion): TonAsset = when (this) {
    is TonAsset.Native -> copy(version = version)
    is TonAsset.Jetton -> copy(version = version)
}

/** Fee/balance token of the same wallet: on TON each version has its own native balance. */
val TokenQuery.nativeTokenQuery: TokenQuery
    get() = tonAsset?.let { TonAsset.Native(it.version).tokenQuery }
        ?: TokenQuery(blockchainType, TokenType.Native)

val Token.nativeTokenQuery: TokenQuery
    get() = tokenQuery.nativeTokenQuery
