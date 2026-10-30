package cash.p.terminal.entities

import io.horizontalsystems.core.entities.BlockchainType

object UnstoppableChainIds {
    private val blockchainTypeByChainId = mapOf(
        "1" to BlockchainType.Ethereum,
        "10" to BlockchainType.Optimism,
        "56" to BlockchainType.BinanceSmartChain,
        "100" to BlockchainType.Gnosis,
        "137" to BlockchainType.Polygon,
        "8453" to BlockchainType.Base,
        "42161" to BlockchainType.ArbitrumOne,
        "43114" to BlockchainType.Avalanche,
        "4663" to BlockchainType.RobinhoodChain,
        "728126428" to BlockchainType.Tron,
        "solana" to BlockchainType.Solana,
        "bitcoin" to BlockchainType.Bitcoin,
        "bitcoincash" to BlockchainType.BitcoinCash,
        "litecoin" to BlockchainType.Litecoin,
        "zcash" to BlockchainType.Zcash,
        "dash" to BlockchainType.Dash,
        "ecash" to BlockchainType.ECash,
        "stellar" to BlockchainType.Stellar,
        "ton" to BlockchainType.Ton,
        "monero" to BlockchainType.Monero,
        "thorchain-1" to BlockchainType.Thorchain,
        "mayachain-mainnet-v1" to BlockchainType.Mayachain,
    )

    private val chainIdByBlockchainType = blockchainTypeByChainId.entries.associate { (k, v) -> v to k }

    fun blockchainType(chainId: String?): BlockchainType? = blockchainTypeByChainId[chainId]

    fun chainId(blockchainType: BlockchainType): String? = chainIdByBlockchainType[blockchainType]
}
