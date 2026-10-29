package cash.p.terminal.core

import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.ledger.di.ledgerAppKeyFetchers
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Derivation
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.entities.BitcoinCashCoinType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenQuery
import cash.p.terminal.wallet.entities.tonAsset
import cash.p.terminal.wallet.isCompatibleWith
import cash.p.terminal.wallet.isLitecoinMweb
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.zcashAddressSpecTokenQueries
import cash.p.terminal.wallet.zcashDefaultTokenQuery
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.ExtendedKeyCoinType
import io.horizontalsystems.hdwalletkit.HDWallet
import java.math.BigDecimal

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
        BlockchainType.Ton -> "TON (V4R2, W5)"
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
            HardwareWalletSupport.supports(accountType, this)

        is AccountType.LedgerDevice -> nativeTokenQueries.any(ledgerAppKeyFetchers::supports)

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
        is AccountType.MnemonicMonero,
        is AccountType.TonAddress -> accountType.isCompatibleWith(blockchainType, type)

        is AccountType.TrezorDevice -> HardwareWalletSupport.supports(accountType, blockchainType, type)

        is AccountType.LedgerDevice -> ledgerAppKeyFetchers.supports(tokenQuery)

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

val TokenType.Derivation.purpose: HDWallet.Purpose
    get() = when (this) {
        TokenType.Derivation.Bip44 -> HDWallet.Purpose.BIP44
        TokenType.Derivation.Bip49 -> HDWallet.Purpose.BIP49
        TokenType.Derivation.Bip84 -> HDWallet.Purpose.BIP84
        TokenType.Derivation.Bip86 -> HDWallet.Purpose.BIP86
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

        BlockchainType.Ton -> TonWalletVersion.entries.map { TonAsset.Native(it).tokenQuery }

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

val Token.isDefault: Boolean
    get() = when (tonAsset?.version) {
        TonWalletVersion.V4R2 -> true
        TonWalletVersion.W5 -> false
        null -> type.isDefault
    }

val TokenType.isDefault
    get() = when (this) {
        is TokenType.Derived -> derivation.accountTypeDerivation == Derivation.default
        is TokenType.AddressTyped -> type.bitcoinCashCoinType == BitcoinCashCoinType.default
        is TokenType.AddressSpecTyped -> type == TokenType.AddressSpecType.Unified
        else -> false
    }

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

val CoinPrice.diffPercentage: BigDecimal?
    get() = when (getKoinInstance<PriceManager>().displayPricePeriod) {
        DisplayPricePeriod.ONE_HOUR -> diff1h
        DisplayPricePeriod.ONE_DAY -> diff24h
        DisplayPricePeriod.ONE_WEEK -> diff7d
        DisplayPricePeriod.ONE_MONTH -> diff30d
        DisplayPricePeriod.ONE_YEAR -> diff1y
        DisplayPricePeriod.ALL -> diffAll
    }
