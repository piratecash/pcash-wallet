package cash.p.terminal.core

import cash.p.terminal.R
import cash.p.terminal.entities.FeePriceScale
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.customCoinUid
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.models.HsPointTimePeriod
import cash.p.terminal.wallet.protocolType
import cash.p.terminal.wallet.zcashDefaultTokenQuery
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.nftkit.models.NftType
import java.math.BigDecimal

val Token.isCustom: Boolean
    get() = coin.uid == tokenQuery.customCoinUid

val BlockchainType.supportedNftTypes: List<NftType>
    get() = when (this) {
        BlockchainType.Ethereum -> listOf(NftType.Eip721, NftType.Eip1155)
//        BlockchainType.BinanceSmartChain -> listOf(NftType.Eip721)
//        BlockchainType.Polygon -> listOf(NftType.Eip721, NftType.Eip1155)
//        BlockchainType.Avalanche -> listOf(NftType.Eip721)
//        BlockchainType.ArbitrumOne -> listOf(NftType.Eip721)
        else -> listOf()
    }

val BlockchainType.feePriceScale: FeePriceScale
    get() = when (this) {
        BlockchainType.Avalanche -> FeePriceScale.Navax
        else -> FeePriceScale.Gwei
    }

val BlockchainType.isBtcLike: Boolean
    get() = when (this) {
        BlockchainType.Bitcoin,
        BlockchainType.BitcoinCash,
        BlockchainType.Dash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Dogecoin,
        BlockchainType.PirateCash,
        BlockchainType.Cosanta
            -> true

        BlockchainType.ArbitrumOne,
        BlockchainType.Avalanche,
        BlockchainType.Base,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Ethereum,
        BlockchainType.Fantom,
        BlockchainType.Gnosis,
        BlockchainType.Optimism,
        BlockchainType.Polygon,
        BlockchainType.RobinhoodChain,
        BlockchainType.ZkSync,
        BlockchainType.Solana,
        BlockchainType.Stellar,
        BlockchainType.Ton,
        BlockchainType.Tron,
        is BlockchainType.Unsupported,
        BlockchainType.Zcash,
        BlockchainType.Beam,
        BlockchainType.Monero,
        BlockchainType.Thorchain,
        BlockchainType.Mayachain
            -> false
    }

val BlockchainType.isUtxoBased: Boolean
    get() = isBtcLike || this == BlockchainType.Zcash


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

val HsPointTimePeriod.title: Int
    get() = when (this) {
        HsPointTimePeriod.Hour1 -> R.string.Coin_Analytics_Period_1h
        HsPointTimePeriod.Day1 -> R.string.Coin_Analytics_Period_1d
        HsPointTimePeriod.Week1 -> R.string.Coin_Analytics_Period_1w
        HsPointTimePeriod.Month1 -> R.string.Coin_Analytics_Period_1m
        HsPointTimePeriod.Minute30 -> R.string.Coin_Analytics_Period_30m
        HsPointTimePeriod.Hour4 -> R.string.Coin_Analytics_Period_4h
        HsPointTimePeriod.Hour8 -> R.string.Coin_Analytics_Period_8h
    }

val TokenType.AddressType.kitCoinType: MainNetBitcoinCash.CoinType
    get() = when (this) {
        TokenType.AddressType.Type0 -> MainNetBitcoinCash.CoinType.Type0
        TokenType.AddressType.Type145 -> MainNetBitcoinCash.CoinType.Type145
    }

val BlockchainType.defaultTokenQuery: TokenQuery
    get() = when (this) {
        BlockchainType.Bitcoin,
        BlockchainType.Litecoin -> {
            TokenQuery(this, TokenType.Derived(TokenType.Derivation.Bip84))
        }

        BlockchainType.BitcoinCash -> {
            TokenQuery(this, TokenType.AddressTyped(TokenType.AddressType.Type145))
        }

        BlockchainType.Zcash -> zcashDefaultTokenQuery

        else -> {
            TokenQuery(this, TokenType.Native)
        }
    }

val TokenType.isNative: Boolean
    get() = this is TokenType.Native ||
            this == TokenType.Mweb ||
            this is TokenType.Derived ||
            this is TokenType.AddressTyped ||
            this is TokenType.AddressSpecTyped

val CoinPrice.diffPercentage: BigDecimal?
    get() = when (App.priceManager.displayPricePeriod) {
        DisplayPricePeriod.ONE_HOUR -> diff1h
        DisplayPricePeriod.ONE_DAY -> diff24h
        DisplayPricePeriod.ONE_WEEK -> diff7d
        DisplayPricePeriod.ONE_MONTH -> diff30d
        DisplayPricePeriod.ONE_YEAR -> diff1y
        DisplayPricePeriod.ALL -> diffAll
    }
