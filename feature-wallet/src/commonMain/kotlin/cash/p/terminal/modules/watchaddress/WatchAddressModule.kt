package cash.p.terminal.modules.watchaddress

import cash.p.terminal.modules.address.AddressHandlerFactory
import io.horizontalsystems.core.entities.BlockchainType

object WatchAddressModule {

    val supportedBlockchainTypes = buildList {
        add(BlockchainType.Ethereum)
        add(BlockchainType.Tron)
        add(BlockchainType.Ton)
        add(BlockchainType.Bitcoin)
        add(BlockchainType.BitcoinCash)
        add(BlockchainType.Litecoin)
        add(BlockchainType.Dash)
        add(BlockchainType.ECash)
        add(BlockchainType.Stellar)
        add(BlockchainType.Thorchain)
        add(BlockchainType.Mayachain)
    }

    fun addressParserChain(udnApiKey: String) = AddressHandlerFactory(udnApiKey).parserChain(
        blockchainTypes = supportedBlockchainTypes,
        blockchainTypesWithEns = listOf(BlockchainType.Ethereum)
    )
}
