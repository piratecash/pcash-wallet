package cash.p.terminal.core

import androidx.compose.ui.graphics.Color
import cash.p.terminal.modules.market.topplatforms.Platform
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.arbitrum_erc20
import cash.p.terminal.resources.avalanche_erc20
import cash.p.terminal.resources.base_erc20
import cash.p.terminal.resources.bep20
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.resources.erc20
import cash.p.terminal.resources.fantom_erc20
import cash.p.terminal.resources.gnosis_erc20
import cash.p.terminal.resources.ic_cosanta
import cash.p.terminal.resources.ic_piratecash
import cash.p.terminal.resources.optimism_erc20
import cash.p.terminal.resources.polygon_erc20
import cash.p.terminal.resources.robinhood_chain
import cash.p.terminal.resources.stellar_asset
import cash.p.terminal.resources.the_open_network_jetton
import cash.p.terminal.resources.token_type_litecoin_mweb
import cash.p.terminal.resources.tron_trc20
import cash.p.terminal.resources.zksync_erc20
import cash.p.terminal.wallet.Derivation
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.isLitecoinMweb
import cash.p.terminal.wallet.models.CoinCategory
import cash.p.terminal.wallet.models.CoinInvestment
import cash.p.terminal.wallet.models.CoinTreasury
import cash.p.terminal.wallet.models.TopPlatform
import cash.p.terminal.wallet.zCashCoinType
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import java.net.URI
import org.jetbrains.compose.resources.DrawableResource

val Token.isSupported: Boolean
    get() = tokenQuery.isSupported

val Token.iconPlaceholder: DrawableResource
    get() = when (type) {
        is TokenType.Eip20 -> blockchainType.tokenIconPlaceholder
        else -> Res.drawable.coin_placeholder
    }

val TokenQuery.isSupported: Boolean
    get() = when (blockchainType) {
        BlockchainType.Bitcoin,
        BlockchainType.Litecoin -> {
            tokenType is TokenType.Derived ||
                    isLitecoinMweb
        }

        BlockchainType.BitcoinCash -> {
            tokenType is TokenType.AddressTyped
        }

        BlockchainType.PirateCash,
        BlockchainType.Cosanta,
        BlockchainType.ECash,
        BlockchainType.Dogecoin,
        BlockchainType.Beam,
        BlockchainType.Monero,
        BlockchainType.Dash -> {
            tokenType is TokenType.Native
        }

        BlockchainType.Zcash -> {
            tokenType is TokenType.AddressSpecTyped
        }

        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.RobinhoodChain,
        BlockchainType.ArbitrumOne,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.Avalanche -> {
            tokenType is TokenType.Native || tokenType is TokenType.Eip20
        }

        BlockchainType.Solana -> {
            tokenType is TokenType.Native || tokenType is TokenType.Spl
        }

        BlockchainType.Tron -> {
            tokenType is TokenType.Native || tokenType is TokenType.Eip20
        }

        BlockchainType.Ton -> {
            tokenType is TokenType.Native || tokenType is TokenType.Jetton
        }

        BlockchainType.Stellar -> {
            tokenType is TokenType.Native || tokenType is TokenType.Asset
        }

        BlockchainType.Thorchain -> {
            tokenType is TokenType.Native || tokenType is TokenType.ThorchainAsset
        }

        BlockchainType.Mayachain -> {
            tokenType is TokenType.Native
        }

        is BlockchainType.Unsupported -> false
    }

fun Blockchain.eip20TokenUrl(address: String) = eip3091url?.replace("\$ref", address)

fun Blockchain.jettonUrl(address: String) = "https://tonviewer.com/$address"
fun Blockchain.assetUrl(code: String, issuer: String) = "https://stellarchain.io/assets/$code-$issuer"

private val blockchainOrderMap: Map<BlockchainType, Int> by lazy {
    val map = mutableMapOf<BlockchainType, Int>()
    listOf(
        BlockchainType.Bitcoin,
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Solana,
        BlockchainType.Polygon,
        BlockchainType.Stellar,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.RobinhoodChain,
        BlockchainType.Avalanche,
        BlockchainType.Beam,
        BlockchainType.Zcash,
        BlockchainType.BitcoinCash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Dash,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
    ).forEachIndexed { index, blockchainType ->
        map[blockchainType] = index
    }
    map
}

val BlockchainType.order: Int
    get() = blockchainOrderMap[this] ?: Int.MAX_VALUE

val BlockchainType.tokenIconPlaceholder: DrawableResource
    get() = when (this) {
        BlockchainType.Ethereum -> Res.drawable.erc20
        BlockchainType.BinanceSmartChain -> Res.drawable.bep20
        BlockchainType.Avalanche -> Res.drawable.avalanche_erc20
        BlockchainType.Polygon -> Res.drawable.polygon_erc20
        BlockchainType.Optimism -> Res.drawable.optimism_erc20
        BlockchainType.Base -> Res.drawable.base_erc20
        BlockchainType.ZkSync -> Res.drawable.zksync_erc20
        BlockchainType.RobinhoodChain -> Res.drawable.robinhood_chain
        BlockchainType.ArbitrumOne -> Res.drawable.arbitrum_erc20
        BlockchainType.Gnosis -> Res.drawable.gnosis_erc20
        BlockchainType.Fantom -> Res.drawable.fantom_erc20
        BlockchainType.Tron -> Res.drawable.tron_trc20
        BlockchainType.Ton -> Res.drawable.the_open_network_jetton
        BlockchainType.Stellar -> Res.drawable.stellar_asset
        else -> Res.drawable.coin_placeholder
    }

val BlockchainType.brandColor: Color?
    get() = when (this) {
        BlockchainType.Ethereum -> Color(0xFF6B7196)
        BlockchainType.BinanceSmartChain -> Color(0xFFF3BA2F)
        BlockchainType.Polygon -> Color(0xFF8247E5)
        BlockchainType.Avalanche -> Color(0xFFD74F49)
        BlockchainType.Optimism -> Color(0xFFEB3431)
        BlockchainType.Base -> Color(0xFF2759F6)
        BlockchainType.ZkSync -> Color(0xFF8D8FF0)
        BlockchainType.ArbitrumOne -> Color(0xFF96BEDC)
        BlockchainType.RobinhoodChain -> Color(0xFF00C805)
        else -> null
    }

val TokenType.order: Int
    get() {
        return when (this) {
            TokenType.Native -> 0
            is TokenType.Derived -> derivation.accountTypeDerivation.order
            is TokenType.AddressTyped -> type.bitcoinCashCoinType.ordinal
            TokenType.Mweb -> Derivation.bip86.order + 1
            else -> Int.MAX_VALUE
        }
    }

val TopPlatform.imageUrl
    get() = "https://p.cash/storage/blockchains/${blockchain.uid}.png"

val FullCoin.iconPlaceholder: DrawableResource
    get() = if (tokens.size == 1) {
        tokens.first().iconPlaceholder
    } else {
        // TODO Add static images for PirateCash and Cosanta
        var pirate: String = "piratecash"
        var cosa: String = "cosanta"
        when (coin.uid) {
            pirate -> {
                Res.drawable.ic_piratecash
            }

            cosa -> {
                Res.drawable.ic_cosanta
            }

            else -> {
                Res.drawable.coin_placeholder
            }
        }
    }

val TokenType.title: String
    get() = when (this) {
        TokenType.Mweb -> "MWEB"
        is TokenType.Derived -> derivation.accountTypeDerivation.rawName
        is TokenType.AddressTyped -> type.bitcoinCashCoinType.title
        is TokenType.AddressSpecTyped -> type.name
        else -> ""
    }

val TokenType.description: String
    get() = when (this) {
        TokenType.Mweb -> Translator.getString(Res.string.token_type_litecoin_mweb)
        is TokenType.Derived ->
            derivation.accountTypeDerivation.addressType + derivation.accountTypeDerivation.recommended
        is TokenType.AddressTyped -> Translator.getString(type.bitcoinCashCoinType.description)
        is TokenType.AddressSpecTyped -> Translator.getString(type.zCashCoinType.description)
        else -> ""
    }

val Platform.iconUrl: String
    get() = "https://p.cash/storage/blockchains/$uid.png"

val String.fiatIconUrl: String
    get() = "https://cdn.blocksdecoded.com/fiat-icons/$this@3x.png"

val CoinCategory.imageUrl: String
    get() = "https://cdn.blocksdecoded.com/category-icons/$uid@3x.png"

val CoinInvestment.Fund.logoUrl: String
    get() = "https://cdn.blocksdecoded.com/fund-icons/$uid@3x.png"

val CoinTreasury.logoUrl: String
    get() = "https://cdn.blocksdecoded.com/treasury-icons/$fundUid@3x.png"

/**
 * Returns the host of this URL string suitable for display (without the leading "www." prefix),
 * or null when the string is not parseable as a URI or has no authority component
 * (e.g. missing scheme like "example.com", opaque URIs like "mailto:..."), or when
 * the URI constructor throws on a malformed input.
 *
 * Centralizes the safe-host extraction so call sites never touch the platform-typed
 * java.net.URI.getHost() directly — that returns a non-null platform String that
 * silently becomes null at runtime for any non-hierarchical URI.
 */
fun String.urlDisplayHostOrNull(): String? =
    tryOrNull { URI(this).host?.removePrefix("www.")?.takeIf { it.isNotBlank() } }
