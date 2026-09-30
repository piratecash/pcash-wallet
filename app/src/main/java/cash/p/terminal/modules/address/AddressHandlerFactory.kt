package cash.p.terminal.modules.address

import cash.p.dogecoinkit.MainNetDogecoin
import cash.p.terminal.core.supported
import cash.p.terminal.modules.send.beam.BeamRecipient
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.bitcoinkit.MainNet
import io.horizontalsystems.dashkit.MainNetDash
import io.horizontalsystems.ecash.MainNetECash
import io.horizontalsystems.litecoinkit.MainNetLitecoin
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.cosantakit.MainNetCosanta
import io.horizontalsystems.piratecashkit.MainNetPirateCash

class AddressHandlerFactory(
    private val udnApiKey: String,
) {

    // Widened for the registration test: it asserts the per-chain handler types without
    // invoking a handler, several of which reach native code no JVM unit test can load.
    internal fun parserChainHandlers(blockchainType: BlockchainType): List<IAddressHandler> =
        when (blockchainType) {
            BlockchainType.Bitcoin,
            BlockchainType.BitcoinCash,
            BlockchainType.ECash,
            BlockchainType.Litecoin,
            BlockchainType.Dogecoin,
            BlockchainType.Cosanta,
            BlockchainType.PirateCash,
            BlockchainType.Dash -> utxoChainHandlers(blockchainType)

            BlockchainType.Monero -> listOf(AddressHandlerMonero())
            BlockchainType.Zcash -> listOf(AddressHandlerZcash())

            BlockchainType.Ethereum,
            BlockchainType.BinanceSmartChain,
            BlockchainType.Polygon,
            BlockchainType.Avalanche,
            BlockchainType.Optimism,
            BlockchainType.Base,
            BlockchainType.ZkSync,
            BlockchainType.RobinhoodChain,
            BlockchainType.Gnosis,
            BlockchainType.Fantom,
            BlockchainType.ArbitrumOne -> listOf(AddressHandlerEvm(blockchainType))

            BlockchainType.Solana -> listOf(AddressHandlerSolana())
            BlockchainType.Tron -> listOf(AddressHandlerTron())
            BlockchainType.Ton -> listOf(AddressHandlerTon())
            BlockchainType.Stellar -> listOf(AddressHandlerStellar())
            BlockchainType.Beam -> listOf(BeamRecipient)
            is BlockchainType.Unsupported -> emptyList()
        }

    private fun utxoChainHandlers(blockchainType: BlockchainType): List<IAddressHandler> =
        when (blockchainType) {
            BlockchainType.Bitcoin -> MainNet().let {
                listOf(AddressHandlerBase58(it, blockchainType), AddressHandlerBech32(it, blockchainType))
            }
            BlockchainType.BitcoinCash -> MainNetBitcoinCash().let {
                listOf(AddressHandlerBase58(it, blockchainType), AddressHandlerBitcoinCash(it, blockchainType))
            }
            BlockchainType.ECash -> MainNetECash().let {
                listOf(AddressHandlerBase58(it, blockchainType), AddressHandlerBitcoinCash(it, blockchainType))
            }
            BlockchainType.Litecoin -> MainNetLitecoin().let {
                listOf(AddressHandlerBase58(it, blockchainType), AddressHandlerBech32(it, blockchainType))
            }
            BlockchainType.Dogecoin -> listOf(AddressHandlerBase58(MainNetDogecoin(), blockchainType))
            BlockchainType.Cosanta -> listOf(AddressHandlerBase58(MainNetCosanta(), blockchainType))
            BlockchainType.PirateCash -> listOf(AddressHandlerBase58(MainNetPirateCash(), blockchainType))
            BlockchainType.Dash -> listOf(AddressHandlerBase58(MainNetDash(), blockchainType))
            else -> emptyList()
        }

    private fun domainHandlers(blockchainType: BlockchainType): List<IAddressHandler> {
        val udnHandler = AddressHandlerUdn(TokenQuery(blockchainType, TokenType.Native), null, udnApiKey)
        val domainAddressHandlers = mutableListOf<IAddressHandler>(udnHandler)
        when (blockchainType) {
            BlockchainType.Ethereum,
            BlockchainType.BinanceSmartChain,
            BlockchainType.Polygon,
            BlockchainType.Avalanche,
            BlockchainType.Optimism,
            BlockchainType.Base,
            BlockchainType.ZkSync,
            BlockchainType.RobinhoodChain,
            BlockchainType.Gnosis,
            BlockchainType.Fantom,
            BlockchainType.ArbitrumOne -> {
                domainAddressHandlers.add(AddressHandlerEns(blockchainType, EnsResolverHolder.resolver))
            }

            else -> {}
        }
        return domainAddressHandlers
    }

    fun parserChain(blockchainType: BlockchainType?, withEns: Boolean = false): AddressParserChain {
        val addressHandlers = mutableListOf<IAddressHandler>()
        val domainHandlers = mutableListOf<IAddressHandler>()

        blockchainType?.let {
            addressHandlers.addAll(parserChainHandlers(it))
            if (withEns) {
                domainHandlers.addAll(domainHandlers(it))
            }
        } ?: run {
            BlockchainType.supported.forEach {
                addressHandlers.addAll(parserChainHandlers(it))
                if (withEns) {
                    domainHandlers.addAll(domainHandlers(it))
                }
            }
        }

        return AddressParserChain(addressHandlers, domainHandlers)
    }

    fun parserChain(
        blockchainTypes: List<BlockchainType>, blockchainTypesWithEns: List<BlockchainType>
    ): AddressParserChain {
        val addressHandlers = mutableListOf<IAddressHandler>()
        val domainHandlers = mutableListOf<IAddressHandler>()

        for (blockchainType in blockchainTypes) {
            addressHandlers.addAll(parserChainHandlers(blockchainType))
        }

        for (blockchainType in blockchainTypesWithEns) {
            domainHandlers.addAll(domainHandlers(blockchainType))
        }

        return AddressParserChain(addressHandlers, domainHandlers)
    }

}
