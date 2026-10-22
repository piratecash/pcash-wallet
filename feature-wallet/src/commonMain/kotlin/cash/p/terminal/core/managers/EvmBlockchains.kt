package cash.p.terminal.core.managers

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.evmChain
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Chain

interface EvmBlockchains {
    val allBlockchains: List<Blockchain>
    val allMainNetBlockchains: List<Blockchain>

    fun getChain(blockchainType: BlockchainType): Chain
    fun getBlockchain(chainId: Int): Blockchain?
    fun getBlockchain(token: Token): Blockchain?
    fun getBlockchain(blockchainType: BlockchainType): Blockchain?
    fun getBaseToken(blockchainType: BlockchainType): Token?

    companion object {
        val blockchainTypes = listOf(
            BlockchainType.Ethereum,
            BlockchainType.BinanceSmartChain,
            BlockchainType.Polygon,
            BlockchainType.Avalanche,
            BlockchainType.Optimism,
            BlockchainType.ArbitrumOne,
            BlockchainType.Gnosis,
            BlockchainType.Fantom,
            BlockchainType.Base,
            BlockchainType.ZkSync,
            BlockchainType.RobinhoodChain,
        )
    }
}

class EvmBlockchainCatalog(private val marketKit: MarketKitWrapper) : EvmBlockchains {
    override val allBlockchains: List<Blockchain>
        get() = marketKit.blockchains(EvmBlockchains.blockchainTypes.map { it.uid })

    override val allMainNetBlockchains: List<Blockchain>
        get() = marketKit.blockchains(EvmBlockchains.blockchainTypes.map { it.uid })

    override fun getChain(blockchainType: BlockchainType): Chain =
        blockchainType.evmChain ?: throw IllegalArgumentException("Unsupported blockchain type $blockchainType")

    override fun getBlockchain(chainId: Int): Blockchain? =
        allBlockchains.firstOrNull { getChain(it.type).id == chainId }

    override fun getBlockchain(token: Token): Blockchain? =
        allBlockchains.firstOrNull { token.blockchain == it }

    override fun getBlockchain(blockchainType: BlockchainType): Blockchain? =
        allBlockchains.firstOrNull { it.type == blockchainType }

    override fun getBaseToken(blockchainType: BlockchainType): Token? =
        marketKit.token(TokenQuery(blockchainType, TokenType.Native))
}
