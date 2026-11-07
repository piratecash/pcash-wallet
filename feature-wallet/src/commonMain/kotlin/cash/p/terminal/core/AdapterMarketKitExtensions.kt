package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.customCoinUid
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.protocolType
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.hdwalletkit.HDWallet

val Token.isCustom: Boolean
    get() = coin.uid == tokenQuery.customCoinUid

val TokenType.derivation: TokenType.Derivation?
    get() = when (this) {
        is TokenType.Derived -> this.derivation
        else -> null
    }

val TokenType.purpose: HDWallet.Purpose
    get() = requireNotNull(derivation) { "$this has no derivation" }.purpose

val TokenType.bitcoinCashCoinType: TokenType.AddressType?
    get() = when (this) {
        is TokenType.AddressTyped -> this.type
        else -> null
    }

val FullCoin.typeLabel: String?
    get() = tokens.singleOrNull()?.protocolType

val FullCoin.supportedTokens
    get() = tokens
        .filter { it.isSupported }
        .sortedWith(compareBy({ it.type.order }, { it.blockchain.type.order }))

fun FullCoin.eligibleTokens(accountType: AccountType): List<Token> {
    return supportedTokens
        .filter { it.supports(accountType) && it.blockchainType.supports(accountType) }
}

val TokenType.AddressType.kitCoinType: MainNetBitcoinCash.CoinType
    get() = when (this) {
        TokenType.AddressType.Type0 -> MainNetBitcoinCash.CoinType.Type0
        TokenType.AddressType.Type145 -> MainNetBitcoinCash.CoinType.Type145
    }
