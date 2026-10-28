package cash.p.terminal.modules.address

import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.send.beam.BeamRecipient
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.ens.EnsResolver
import cash.p.terminal.wallet.entities.TokenQuery
import org.koin.core.scope.Scope

internal fun Scope.tokenAddressViewModel(
    tokenQuery: TokenQuery,
    coinCode: String,
    initial: Address?
): AddressViewModel {
    val ensHandler = AddressHandlerEns(tokenQuery.blockchainType, get<EnsResolver>())
    val udnHandler = AddressHandlerUdn(tokenQuery, coinCode, get<AddressResolutionConfig>().udnApiKey)
    val addressParserChain = AddressParserChain(domainHandlers = listOf(ensHandler, udnHandler))

    when (tokenQuery.blockchainType) {
        BlockchainType.Bitcoin,
        BlockchainType.BitcoinCash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Dogecoin,
        BlockchainType.Cosanta,
        BlockchainType.PirateCash,
        BlockchainType.Dash,
        BlockchainType.Zcash -> {
            addressParserChain.addHandler(AddressHandlerPure(tokenQuery.blockchainType))
        }

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
            addressParserChain.addHandler(AddressHandlerEvm(tokenQuery.blockchainType))
        }

        BlockchainType.Monero -> {
            addressParserChain.addHandler(AddressHandlerMonero())
        }

        BlockchainType.Solana -> {
            addressParserChain.addHandler(AddressHandlerSolana())
        }

        BlockchainType.Tron -> {
            addressParserChain.addHandler(AddressHandlerTron())
        }

        BlockchainType.Ton -> {
            addressParserChain.addHandler(AddressHandlerTon())
        }

        BlockchainType.Stellar -> {
            addressParserChain.addHandler(AddressHandlerStellar())
        }

        BlockchainType.Thorchain,
        BlockchainType.Mayachain -> {
            addressParserChain.addHandler(AddressHandlerThorchain.forBlockchainType(tokenQuery.blockchainType))
        }

        BlockchainType.Beam -> {
            addressParserChain.addHandler(BeamRecipient)
        }

        is BlockchainType.Unsupported -> Unit
    }

    val addressUriParser = AddressUriParser(tokenQuery.blockchainType, tokenQuery.tokenType)
    return AddressViewModel(
        blockchainType = tokenQuery.blockchainType,
        contactsRepository = get(),
        addressUriParser = addressUriParser,
        addressParserChain = addressParserChain,
        initial = initial
    )
}

/** Koin qualifier of the NFT send address view model, which has no token to resolve. */
const val NFT_ADDRESS_VIEW_MODEL = "nft_address_view_model"

internal fun Scope.nftAddressViewModel(blockchainType: BlockchainType): AddressViewModel {
    val ensHandler = AddressHandlerEns(blockchainType, get<EnsResolver>())
    val addressParserChain = AddressParserChain(domainHandlers = listOf(ensHandler))

    when (blockchainType) {
        BlockchainType.Bitcoin,
        BlockchainType.BitcoinCash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Dogecoin,
        BlockchainType.Cosanta,
        BlockchainType.PirateCash,
        BlockchainType.Dash,
        BlockchainType.Zcash -> {
            addressParserChain.addHandler(AddressHandlerPure(blockchainType))
        }

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
            addressParserChain.addHandler(AddressHandlerEvm(blockchainType))
        }

        BlockchainType.Solana,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Monero,
        BlockchainType.Stellar,
        BlockchainType.Thorchain,
        BlockchainType.Mayachain,
        BlockchainType.Beam,
        is BlockchainType.Unsupported -> Unit

    }

    val addressUriParser = AddressUriParser(blockchainType, null)
    val addressViewModel = AddressViewModel(
        blockchainType,
        get(),
        addressUriParser,
        addressParserChain,
        null
    )

    return addressViewModel
}
