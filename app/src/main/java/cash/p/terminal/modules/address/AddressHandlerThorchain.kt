package cash.p.terminal.modules.address

import cash.p.terminal.core.managers.thorchainNetwork
import cash.p.terminal.entities.Address
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.models.Address as ThorchainAddress
import io.horizontalsystems.thorchainkit.network.Network

class AddressHandlerThorchain(
    private val network: Network,
    override val blockchainType: BlockchainType,
) : IAddressHandler {

    override fun isSupported(value: String) = try {
        ThorchainAddress.fromString(value, network)
        true
    } catch (e: Throwable) {
        false
    }

    override fun parseAddress(value: String): Address {
        return Address(value, blockchainType = blockchainType)
    }

    companion object {
        fun forBlockchainType(blockchainType: BlockchainType) =
            AddressHandlerThorchain(blockchainType.thorchainNetwork, blockchainType)
    }
}
