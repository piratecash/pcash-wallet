package cash.p.terminal.core

import cash.p.terminal.R
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.entities.FeePriceScale
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.domain.TrezorMoneroAdmissionPolicy
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Derivation
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.customCoinUid
import cash.p.terminal.wallet.isCompatibleWith
import cash.p.terminal.wallet.isLitecoinMweb
import cash.p.terminal.wallet.entities.BitcoinCashCoinType
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.models.HsPointTimePeriod
import cash.p.terminal.wallet.protocolType
import cash.p.terminal.wallet.zcashAddressSpecTokenQueries
import cash.p.terminal.wallet.zcashDefaultTokenQuery
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.ExtendedKeyCoinType
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.nftkit.models.NftType
import java.math.BigDecimal

val Token.isCustom: Boolean
    get() = coin.uid == tokenQuery.customCoinUid

val Blockchain.description: String
    get() = when (type) {
        BlockchainType.Bitcoin -> "BTC (BIP44, BIP49, BIP84, BIP86)"
        BlockchainType.BitcoinCash -> "BCH (Legacy, CashAddress)"
        BlockchainType.ECash -> "XEC"
        BlockchainType.Beam -> "BEAM"
        BlockchainType.Zcash -> "ZEC"
        BlockchainType.Litecoin -> "LTC (BIP44, BIP49, BIP84, BIP86, MWEB)"
        BlockchainType.Dash -> "DASH"
        BlockchainType.Ethereum -> "ETH, ERC20 tokens"
        BlockchainType.BinanceSmartChain -> "BNB, BEP20 tokens"
        BlockchainType.Polygon -> "MATIC, ERC20 tokens"
        BlockchainType.Avalanche -> "AVAX, ERC20 tokens"
        BlockchainType.Optimism -> "L2 chain"
        BlockchainType.Base -> "L2 chain"
        BlockchainType.ZkSync -> "L2 chain"
        BlockchainType.RobinhoodChain -> "L2 chain"
        BlockchainType.ArbitrumOne -> "L2 chain"
        BlockchainType.Solana -> "SOL, SPL tokens"
        BlockchainType.Gnosis -> "xDAI, ERC20 tokens"
        BlockchainType.Fantom -> "FTM, ERC20 tokens"
        BlockchainType.Tron -> "TRX, TRC20 tokens"
        BlockchainType.Ton -> "TON"
        BlockchainType.Stellar -> "XLM, Stellar assets"
        BlockchainType.Cosanta -> "COSA"
        BlockchainType.Dogecoin -> "DOGE"
        BlockchainType.Monero -> "XMR"
        BlockchainType.PirateCash -> "PirateCash"
        BlockchainType.Thorchain -> "RUNE, TCY, RUJI, secured assets"
        BlockchainType.Mayachain -> "CACAO"
        is BlockchainType.Unsupported -> ""
    }

val BlockchainType.restoreSettingTypes: List<RestoreSettingType>
    get() = when (this) {
        BlockchainType.Zcash,
        BlockchainType.Monero -> listOf(RestoreSettingType.BirthdayHeight)

        else -> listOf()
    }

val TokenQuery.restoreSettingTypes: List<RestoreSettingType>
    get() = when {
        isLitecoinMweb -> {
            listOf(RestoreSettingType.BirthdayHeight)
        }

        else -> blockchainType.restoreSettingTypes
    }

val Token.restoreSettingTypes: List<RestoreSettingType>
    get() = tokenQuery.restoreSettingTypes

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

val BlockchainType.isEvm: Boolean
    get() = when (this) {
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
            -> true

        BlockchainType.Bitcoin,
        BlockchainType.BitcoinCash,
        BlockchainType.Dash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Solana,
        BlockchainType.Stellar,
        BlockchainType.Ton,
        BlockchainType.Tron,
        is BlockchainType.Unsupported,
        BlockchainType.Zcash,
        BlockchainType.Beam,
        BlockchainType.Monero,
        BlockchainType.Cosanta,
        BlockchainType.Dogecoin,
        BlockchainType.PirateCash,
        BlockchainType.Thorchain,
        BlockchainType.Mayachain
            -> false
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


fun BlockchainType.supports(accountType: AccountType): Boolean {
    if (this == BlockchainType.Zcash) return accountType.zcashAddressSpecs().isNotEmpty()

    if (this == BlockchainType.Beam) {
        return accountType.isCompatibleWith(this, TokenType.Native)
    }

    return when (accountType) {
        is AccountType.ZCashUfvKey,
        is AccountType.ZCashSaplingKey -> false

        is AccountType.MnemonicMonero ->
            this == BlockchainType.Monero

        is AccountType.TrezorDevice ->
            TrezorModelSupport.isSupported(TrezorModel.fromInternalModel(accountType.model), this)

        is AccountType.HardwareCard ->
            this != BlockchainType.Thorchain && this != BlockchainType.Mayachain

        is AccountType.Mnemonic -> true

        is AccountType.HdExtendedKey -> {
            val coinTypes = accountType.hdExtendedKey.coinTypes
            when (this) {
                BlockchainType.Bitcoin -> coinTypes.contains(ExtendedKeyCoinType.Bitcoin)
                BlockchainType.Litecoin -> coinTypes.contains(ExtendedKeyCoinType.Litecoin)
                BlockchainType.Dogecoin -> coinTypes.contains(ExtendedKeyCoinType.Dogecoin) || coinTypes.contains(
                    ExtendedKeyCoinType.Bitcoin
                )

                BlockchainType.Dash -> (coinTypes.contains(ExtendedKeyCoinType.Dash) || coinTypes.contains(
                    ExtendedKeyCoinType.Bitcoin
                )) && accountType.hdExtendedKey.purposes.contains(
                    HDWallet.Purpose.BIP44
                )

                BlockchainType.BitcoinCash,
                BlockchainType.ECash -> coinTypes.contains(
                    ExtendedKeyCoinType.Bitcoin
                ) && accountType.hdExtendedKey.purposes.contains(
                    HDWallet.Purpose.BIP44
                )

                else -> false
            }
        }

        is AccountType.BitcoinAddress -> {
            this === accountType.blockchainType
        }

        is AccountType.EvmAddress,
        is AccountType.EvmPrivateKey -> isEvm

        is AccountType.SolanaAddress ->
            this == BlockchainType.Solana

        is AccountType.TronAddress ->
            this == BlockchainType.Tron

        is AccountType.TonAddress ->
            this == BlockchainType.Ton

        is AccountType.StellarAddress ->
            this == BlockchainType.Stellar

        is AccountType.StellarSecretKey ->
            this == BlockchainType.Stellar

        is AccountType.ThorchainAddress ->
            this == BlockchainType.Thorchain

        is AccountType.MayachainAddress ->
            this == BlockchainType.Mayachain
    }
}

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

fun Token.supports(accountType: AccountType): Boolean {
    if (blockchainType == BlockchainType.Zcash) {
        val spec = (type as? TokenType.AddressSpecTyped)?.type
        return spec != null && spec in accountType.zcashAddressSpecs()
    }

    if (blockchainType == BlockchainType.Beam) {
        return accountType.isCompatibleWith(blockchainType, type)
    }

    return when (accountType) {
        is AccountType.BitcoinAddress -> {
            tokenQuery.tokenType == accountType.tokenType
        }

        is AccountType.HdExtendedKey -> supportsHdExtendedKey(accountType)

        is AccountType.HardwareCard,
        is AccountType.Mnemonic,
        is AccountType.MnemonicMonero -> accountType.isCompatibleWith(blockchainType, type)

        is AccountType.TrezorDevice -> {
            TrezorMoneroAdmissionPolicy.supportsStoredToken(
                accountType.model,
                blockchainType,
                type,
            ) || TrezorPublicKeySpecs.supports(
                TrezorModel.fromInternalModel(accountType.model),
                blockchainType,
                type,
            )
        }

        else -> true
    }
}

private fun Token.supportsHdExtendedKey(accountType: AccountType.HdExtendedKey): Boolean =
    when (blockchainType) {
        BlockchainType.Bitcoin,
        BlockchainType.Dogecoin,
        BlockchainType.Litecoin -> supportsDerivation(accountType)

        BlockchainType.Dash -> {
            (accountType.hdExtendedKey.coinTypes.contains(
                ExtendedKeyCoinType.Dash
            ) || accountType.hdExtendedKey.coinTypes.contains(ExtendedKeyCoinType.Bitcoin)) &&
                    accountType.hdExtendedKey.purposes.contains(HDWallet.Purpose.BIP44)
        }

        BlockchainType.BitcoinCash,
        BlockchainType.ECash -> {
            accountType.hdExtendedKey.coinTypes.contains(ExtendedKeyCoinType.Bitcoin) &&
                    accountType.hdExtendedKey.purposes.contains(HDWallet.Purpose.BIP44)
        }

        else -> false
    }

private fun Token.supportsDerivation(accountType: AccountType.HdExtendedKey): Boolean {
    val type = type
    if (type !is TokenType.Derived) return false
    if (!accountType.hdExtendedKey.purposes.contains(type.derivation.purpose)) return false

    return when (blockchainType) {
        BlockchainType.Bitcoin -> accountType.hdExtendedKey.coinTypes.contains(
            ExtendedKeyCoinType.Bitcoin
        )

        BlockchainType.Litecoin -> accountType.hdExtendedKey.coinTypes.contains(
            ExtendedKeyCoinType.Litecoin
        )

        BlockchainType.Dogecoin -> accountType.hdExtendedKey.coinTypes.contains(
            ExtendedKeyCoinType.Dogecoin
        ) || accountType.hdExtendedKey.coinTypes.contains(ExtendedKeyCoinType.Bitcoin)

        else -> false
    }
}

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

val TokenType.Derivation.purpose: HDWallet.Purpose
    get() = when (this) {
        TokenType.Derivation.Bip44 -> HDWallet.Purpose.BIP44
        TokenType.Derivation.Bip49 -> HDWallet.Purpose.BIP49
        TokenType.Derivation.Bip84 -> HDWallet.Purpose.BIP84
        TokenType.Derivation.Bip86 -> HDWallet.Purpose.BIP86
    }

val TokenType.AddressType.kitCoinType: MainNetBitcoinCash.CoinType
    get() = when (this) {
        TokenType.AddressType.Type0 -> MainNetBitcoinCash.CoinType.Type0
        TokenType.AddressType.Type145 -> MainNetBitcoinCash.CoinType.Type145
    }

val BlockchainType.nativeTokenQueries: List<TokenQuery>
    get() = when (this) {
        BlockchainType.Bitcoin -> {
            TokenType.Derivation.entries.map {
                TokenQuery(this, TokenType.Derived(it))
            }
        }

        BlockchainType.Litecoin -> {
            TokenType.Derivation.entries.map {
                TokenQuery(this, TokenType.Derived(it))
            } + TokenQuery(this, TokenType.Mweb)
        }

        BlockchainType.BitcoinCash -> {
            TokenType.AddressType.entries.map {
                TokenQuery(this, TokenType.AddressTyped(it))
            }
        }

        BlockchainType.Zcash -> zcashAddressSpecTokenQueries

        else -> {
            listOf(TokenQuery(this, TokenType.Native))
        }
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

val TokenType.isDefault
    get() = when (this) {
        is TokenType.Derived -> derivation.accountTypeDerivation == Derivation.default
        is TokenType.AddressTyped -> type.bitcoinCashCoinType == BitcoinCashCoinType.default
        is TokenType.AddressSpecTyped -> type == TokenType.AddressSpecType.Unified
        else -> false
    }

val TokenType.isNative: Boolean
    get() = this is TokenType.Native ||
            this == TokenType.Mweb ||
            this is TokenType.Derived ||
            this is TokenType.AddressTyped ||
            this is TokenType.AddressSpecTyped

val BlockchainType.Companion.supported: List<BlockchainType>
    get() = listOf(
        BlockchainType.Bitcoin,
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.PirateCash,
        BlockchainType.Cosanta,
        BlockchainType.Polygon,
        BlockchainType.Avalanche,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.RobinhoodChain,
        BlockchainType.ArbitrumOne,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.Zcash,
        BlockchainType.Dash,
        BlockchainType.BitcoinCash,
        BlockchainType.Litecoin,
        BlockchainType.Solana,
        BlockchainType.ECash,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Stellar,
        BlockchainType.Beam,
        BlockchainType.Monero,
        BlockchainType.Dogecoin,
        BlockchainType.Thorchain,
        BlockchainType.Mayachain,
    )

val CoinPrice.diffPercentage: BigDecimal?
    get() = when (App.priceManager.displayPricePeriod) {
        DisplayPricePeriod.ONE_HOUR -> diff1h
        DisplayPricePeriod.ONE_DAY -> diff24h
        DisplayPricePeriod.ONE_WEEK -> diff7d
        DisplayPricePeriod.ONE_MONTH -> diff30d
        DisplayPricePeriod.ONE_YEAR -> diff1y
        DisplayPricePeriod.ALL -> diffAll
    }
