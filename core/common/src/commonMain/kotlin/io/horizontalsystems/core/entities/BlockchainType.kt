package io.horizontalsystems.core.entities

import io.horizontalsystems.core.common.CommonIgnoredOnParcel
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

sealed class BlockchainType : CommonParcelable {
    abstract val uid: String
    open val stringRepresentation: String get() = uid

    @CommonParcelize
    object Bitcoin : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "bitcoin"
    }

    @CommonParcelize
    object BitcoinCash : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "bitcoin-cash"
        @CommonIgnoredOnParcel
        override val stringRepresentation = "bitcoinCash"
    }

    @CommonParcelize
    object ECash : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "ecash"
    }

    @CommonParcelize
    object Litecoin : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "litecoin"
    }

    @CommonParcelize
    object Dogecoin : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "dogecoin"
    }

    @CommonParcelize
    object Dash : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "dash"
    }

    @CommonParcelize
    object Beam : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "beam"
    }

    @CommonParcelize
    object Zcash : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "zcash"
    }

    @CommonParcelize
    object Stellar : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "stellar"
    }

    @CommonParcelize
    object Ethereum : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "ethereum"
    }

    @CommonParcelize
    object BinanceSmartChain : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "binance-smart-chain"
        @CommonIgnoredOnParcel
        override val stringRepresentation = "binanceSmartChain"
    }

    @CommonParcelize
    object Polygon : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "polygon-pos"
        @CommonIgnoredOnParcel
        override val stringRepresentation = "polygon"
    }

    @CommonParcelize
    object Avalanche : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "avalanche"
    }

    @CommonParcelize
    object Optimism : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "optimistic-ethereum"
        @CommonIgnoredOnParcel
        override val stringRepresentation = "optimism"
    }

    @CommonParcelize
    object ArbitrumOne : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "arbitrum-one"
        @CommonIgnoredOnParcel
        override val stringRepresentation = "arbitrumOne"
    }

    @CommonParcelize
    object Solana : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "solana"
    }

    @CommonParcelize
    object Gnosis : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "gnosis"
    }

    @CommonParcelize
    object Fantom : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "fantom"
    }

    @CommonParcelize
    object Tron : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "tron"
    }

    @CommonParcelize
    object Ton : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "the-open-network"
    }

    @CommonParcelize
    object Base : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "base"
    }

    @CommonParcelize
    object Cosanta : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "cosanta"
    }

    @CommonParcelize
    object PirateCash : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "piratecash"
    }

    @CommonParcelize
    object ZkSync : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "zksync"
    }

    @CommonParcelize
    object RobinhoodChain : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "robinhood"
        @CommonIgnoredOnParcel
        override val stringRepresentation = "robinhoodChain"
    }

    @CommonParcelize
    object Monero : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "monero"
    }

    @CommonParcelize
    object Thorchain : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "thorchain"
    }

    @CommonParcelize
    object Mayachain : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid = "mayachain"
    }

    @CommonParcelize
    class Unsupported(val _uid: String) : BlockchainType() {
        @CommonIgnoredOnParcel
        override val uid: String get() = _uid
        @CommonIgnoredOnParcel
        override val stringRepresentation: String get() = "unsupported|$uid"
    }

    override fun equals(other: Any?): Boolean {
        return other is BlockchainType && other.uid == uid
    }

    override fun hashCode(): Int {
        return uid.hashCode()
    }

    override fun toString() = stringRepresentation

    companion object {
        fun fromUid(uid: String): BlockchainType = when (uid) {
            "bitcoin" -> Bitcoin
            "bitcoin-cash" -> BitcoinCash
            "ecash" -> ECash
            "litecoin" -> Litecoin
            "dogecoin" -> Dogecoin
            "dash" -> Dash
            "beam" -> Beam
            "zcash" -> Zcash
            "ethereum" -> Ethereum
            "binance-smart-chain" -> BinanceSmartChain
            "polygon-pos" -> Polygon
            "avalanche" -> Avalanche
            "optimistic-ethereum" -> Optimism
            "arbitrum-one" -> ArbitrumOne
            "solana" -> Solana
            "gnosis" -> Gnosis
            "fantom" -> Fantom
            "tron" -> Tron
            "the-open-network" -> Ton
            "base" -> Base
            "cosanta" -> Cosanta
            "piratecash" -> PirateCash
            "zksync" -> ZkSync
            "robinhood" -> RobinhoodChain
            "monero" -> Monero
            "stellar" -> Stellar
            "thorchain" -> Thorchain
            "mayachain" -> Mayachain
            else -> Unsupported(uid)
        }
    }
}
