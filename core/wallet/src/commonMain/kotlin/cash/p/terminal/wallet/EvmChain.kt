package cash.p.terminal.wallet

import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Chain

/** The EVM chain of [this] blockchain, or null for a non-EVM one. */
val BlockchainType.evmChain: Chain?
    get() = when (this) {
        BlockchainType.Ethereum -> Chain.Ethereum
        BlockchainType.BinanceSmartChain -> Chain.BinanceSmartChain
        BlockchainType.Polygon -> Chain.Polygon
        BlockchainType.Avalanche -> Chain.Avalanche
        BlockchainType.Optimism -> Chain.Optimism
        BlockchainType.Base -> Chain.Base
        BlockchainType.ZkSync -> Chain.ZkSync
        BlockchainType.RobinhoodChain -> Chain.RobinhoodChain
        BlockchainType.ArbitrumOne -> Chain.ArbitrumOne
        BlockchainType.Gnosis -> Chain.Gnosis
        BlockchainType.Fantom -> Chain.Fantom
        else -> null
    }
