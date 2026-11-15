package cash.p.terminal.modules.address

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.send.beam.BeamRecipient
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.ens.EnsResolver
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

internal val NftTransferAddressQualifier = named("nft_transfer_address")

internal fun Scope.nftTransferAddressViewModel(adapter: ISendTonAdapter): AddressViewModel {
    val addressParserChain = AddressParserChain(
        handlers = listOf(AddressHandlerTon()),
        domainHandlers = listOf(AddressHandlerTonDns(adapter)),
    )
    return AddressViewModel(
        blockchainType = BlockchainType.Ton,
        contactsRepository = get(),
        addressUriParser = AddressUriParser(BlockchainType.Ton, TokenType.Native),
        addressParserChain = addressParserChain,
        initial = null,
    )
}

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
