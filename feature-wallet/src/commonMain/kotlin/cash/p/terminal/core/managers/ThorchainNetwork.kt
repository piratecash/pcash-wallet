package cash.p.terminal.core.managers

import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.network.Network

// Single owner of the chain -> kit-network mapping. ThorchainKitManagers and the address layer
// (AddressHandlerThorchain) both derive their Network from here instead of repeating it.
val BlockchainType.thorchainNetwork: Network
    get() = when (this) {
        BlockchainType.Thorchain -> Network.Mainnet
        BlockchainType.Mayachain -> Network.MayaMainnet
        else -> throw IllegalArgumentException("No THORChain network for $uid")
    }
