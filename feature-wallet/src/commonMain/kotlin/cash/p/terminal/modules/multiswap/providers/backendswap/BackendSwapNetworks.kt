package cash.p.terminal.modules.multiswap.providers.backendswap

import io.horizontalsystems.core.entities.BlockchainType

// Backend `blockchain` names; types absent here are not offered through the backend.
private val BACKEND_NETWORKS: Map<BlockchainType, String> = mapOf(
    BlockchainType.BitcoinCash to "bitcoin_cash",
    BlockchainType.ECash to "XEC",
    BlockchainType.BinanceSmartChain to "binance_smart_chain",
    BlockchainType.Avalanche to "avaxc",
    BlockchainType.Base to "BASE",
    BlockchainType.ZkSync to "ZKSYNC",
) + listOf(
    BlockchainType.Bitcoin,
    BlockchainType.Litecoin,
    BlockchainType.Dogecoin,
    BlockchainType.Dash,
    BlockchainType.Zcash,
    BlockchainType.Monero,
    BlockchainType.Stellar,
    BlockchainType.Ethereum,
    BlockchainType.Polygon,
    BlockchainType.Optimism,
    BlockchainType.ArbitrumOne,
    BlockchainType.Solana,
    BlockchainType.Tron,
    BlockchainType.Ton,
    BlockchainType.RobinhoodChain,
).associateWith { it.uid }

internal val BlockchainType.backendSwapNetwork: String?
    get() = BACKEND_NETWORKS[this]
