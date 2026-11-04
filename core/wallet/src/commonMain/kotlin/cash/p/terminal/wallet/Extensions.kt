package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.BitcoinCashCoinType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.ZCashCoinType
import cash.p.terminal.wallet.entities.tonAsset
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.BlockchainType

fun Token.isMonero() =
    tokenQuery.isMonero

val TokenQuery.isMonero: Boolean
    get() = blockchainType == BlockchainType.Monero && tokenType == TokenType.Native

val TokenQuery.Companion.monero: TokenQuery
    get() = moneroTokenQuery

private val moneroTokenQuery = TokenQuery(BlockchainType.Monero, TokenType.Native)

val Token.isLitecoinMweb: Boolean
    get() = isLitecoinMweb(blockchainType, type)

val TokenQuery.isLitecoinMweb: Boolean
    get() = isLitecoinMweb(blockchainType, tokenType)

private fun isLitecoinMweb(blockchainType: BlockchainType, tokenType: TokenType): Boolean {
    return blockchainType == BlockchainType.Litecoin && tokenType == TokenType.Mweb
}

fun Collection<Wallet>.litecoinMwebAccountIds(): Set<String> =
    mapNotNull { wallet ->
        wallet.account.id.takeIf { wallet.token.isLitecoinMweb }
    }.toSet()

val Token.badge: String?
    get() = when (val tokenType = type) {
        TokenType.Mweb -> {
            "MWEB"
        }

        is TokenType.Derived -> {
            tokenType.derivation.accountTypeDerivation.value.uppercase()
        }

        is TokenType.AddressTyped -> {
            tokenType.type.bitcoinCashCoinType.value.uppercase()
        }

        is TokenType.AddressSpecTyped -> {
            tokenType.type.name.uppercase()
        }

        else -> {
            protocolType?.uppercase()
        }
    }

val TokenType.Derivation.accountTypeDerivation: Derivation
    get() = when (this) {
        TokenType.Derivation.Bip44 -> Derivation.bip44
        TokenType.Derivation.Bip49 -> Derivation.bip49
        TokenType.Derivation.Bip84 -> Derivation.bip84
        TokenType.Derivation.Bip86 -> Derivation.bip86
    }


val TokenType.AddressType.bitcoinCashCoinType: BitcoinCashCoinType
    get() = when (this) {
        TokenType.AddressType.Type0 -> BitcoinCashCoinType.type0
        TokenType.AddressType.Type145 -> BitcoinCashCoinType.type145
    }

val TokenType.AddressSpecType.zCashCoinType: ZCashCoinType
    get() = when (this) {
        TokenType.AddressSpecType.Shielded -> ZCashCoinType.Shielded
        TokenType.AddressSpecType.Transparent -> ZCashCoinType.Transparent
        TokenType.AddressSpecType.Unified -> ZCashCoinType.Unified
    }


val Token.protocolType: String?
    get() = tokenQuery.protocolType

val TokenQuery.protocolType: String?
    get() = tonAsset?.protocolType ?: when (tokenType) {
        TokenType.Mweb -> {
            "MWEB"
        }

        is TokenType.Native -> {
            when (blockchainType) {
                BlockchainType.Ethereum,
                BlockchainType.BinanceSmartChain,
                BlockchainType.Tron,
                BlockchainType.Thorchain,
                BlockchainType.Mayachain -> null

                else -> blockchainType.title
            }
        }

        is TokenType.Eip20 -> {
            when (blockchainType) {
                BlockchainType.Ethereum -> "ERC20"
                BlockchainType.BinanceSmartChain -> "BEP20"
                BlockchainType.Tron -> "TRC20"
                else -> blockchainType.title
            }
        }

        is TokenType.Trc10 -> "TRC10"
        else -> blockchainType.title
    }

private val TonAsset.protocolType: String
    get() = when (this) {
        is TonAsset.Native -> version.title
        is TonAsset.Jetton -> when (version) {
            TonWalletVersion.V4R2 -> "JETTON"
            TonWalletVersion.W5 -> "JETTON ${version.title}"
        }
    }

val BlockchainType.title: String
    get() = when (this) {
        BlockchainType.Bitcoin -> "Bitcoin"
        BlockchainType.BitcoinCash -> "Bitcoin Cash"
        BlockchainType.ECash -> "Ecash"
        BlockchainType.Litecoin -> "Litecoin"
        BlockchainType.Dogecoin -> "Doge"
        BlockchainType.Dash -> "Dash"
        BlockchainType.Beam -> "BEAM"
        BlockchainType.Zcash -> "Zcash"
        BlockchainType.Ethereum -> "Ethereum"
        BlockchainType.BinanceSmartChain -> "BNB Smart Chain"
        BlockchainType.Polygon -> "Polygon"
        BlockchainType.Avalanche -> "Avalanche"
        BlockchainType.ArbitrumOne -> "ArbitrumOne"
        BlockchainType.Optimism -> "Optimism"
        BlockchainType.Base -> "Base"
        BlockchainType.ZkSync -> "ZKsync"
        BlockchainType.RobinhoodChain -> "Robinhood Chain"
        BlockchainType.Solana -> "Solana"
        BlockchainType.Gnosis -> "Gnosis"
        BlockchainType.Fantom -> "Fantom"
        BlockchainType.Tron -> "Tron"
        BlockchainType.Ton -> "Ton"
        BlockchainType.Stellar -> "Stellar"
        BlockchainType.Cosanta -> "Cosanta"
        BlockchainType.PirateCash -> "PirateCash"
        BlockchainType.Monero -> "Monero"
        BlockchainType.Thorchain -> "THORChain"
        BlockchainType.Mayachain -> "Maya"
        is BlockchainType.Unsupported -> this.uid
    }

val TokenType.meta: String?
    get() = when (this) {
        TokenType.Mweb -> "MWEB"
        is TokenType.Derived -> this.derivation.name
        is TokenType.AddressTyped -> this.type.name
        is TokenType.AddressSpecTyped -> this.type.name
        TokenType.TonW5Native, is TokenType.TonW5Jetton -> TonWalletVersion.W5.transactionSourceMeta
        TokenType.Native,
        is TokenType.Eip20,
        is TokenType.Trc10,
        is TokenType.Spl,
        is TokenType.Jetton,
        is TokenType.Asset,
        is TokenType.ThorchainAsset,
        is TokenType.Unsupported -> null
    }

/** V4R2 keeps `null` so transaction sources stored before W5 keep their identity. */
private val TonWalletVersion.transactionSourceMeta: String?
    get() = when (this) {
        TonWalletVersion.V4R2 -> null
        TonWalletVersion.W5 -> "w5"
    }

val TransactionSource.tonWalletVersion: TonWalletVersion?
    get() = if (blockchain.type == BlockchainType.Ton) {
        TonWalletVersion.entries.firstOrNull { it.transactionSourceMeta == meta }
    } else {
        null
    }

fun Wallet.isPirateCash() = (token.type == TokenType.Eip20(PirateContracts.PIRATE_CONTRACT) ||
        token.type == TokenType.Eip20(PirateContracts.PIRATE_CONTRACT.lowercase())) &&
        token.blockchainType == BlockchainType.BinanceSmartChain

fun Wallet.isCosanta() = (token.type == TokenType.Eip20(PirateContracts.COSANTA_CONTRACT) ||
        token.type == TokenType.Eip20(PirateContracts.COSANTA_CONTRACT.lowercase())) &&
        token.blockchainType == BlockchainType.BinanceSmartChain

fun Wallet.isStakingWallet() = isPirateCash() || isCosanta()

fun Wallet.getUniqueKey() = listOf(token.tokenQuery.id, account.id).joinToString()
