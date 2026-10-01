package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.core.factories.EvmAccountManagerFactory
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.merkleiokit.MerkleTransactionAdapter
import io.horizontalsystems.nftkit.core.NftKit
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType

class EvmBlockchainManager(
    private val backgroundManager: BackgroundManager,
    private val syncSourceManager: EvmSyncSourceManager,
    private val marketKit: MarketKitWrapper,
    private val accountManagerFactory: EvmAccountManagerFactory,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val networkErrorTracker: NetworkErrorTracker,
    private val offlineModeManager: OfflineModeManager,
    private val databaseKeys: EvmKitDatabaseKeys,
    private val context: Context,
) {
    private val evmKitManagersMap = mutableMapOf<BlockchainType, Pair<EvmKitManager, EvmAccountManager>>()

    val allBlockchains: List<Blockchain>
        get() = marketKit.blockchains(blockchainTypes.map { it.uid })

    val allMainNetBlockchains: List<Blockchain>
        get() = marketKit.blockchains(blockchainTypes.map { it.uid })

    private fun getEvmKitManagerOrNull(blockchainType: BlockchainType): EvmKitManager? =
        evmKitManagersMap[blockchainType]?.first

    fun syncTransactionHistory(blockchainType: BlockchainType) {
        getEvmKitManagerOrNull(blockchainType)?.evmKitWrapper?.evmKit?.syncTransactions()
    }

    fun syncTransactionHistory() {
        blockchainTypes.forEach(::syncTransactionHistory)
    }

    private fun getEvmKitManagers(blockchainType: BlockchainType): Pair<EvmKitManager, EvmAccountManager> {
        val evmKitManagers = evmKitManagersMap[blockchainType]

        evmKitManagers?.let {
            return it
        }

        val evmKitManager = EvmKitManager(
            getChain(blockchainType),
            backgroundManager,
            syncSourceManager,
            backgroundKeepAliveManager,
            networkErrorTracker,
            offlineModeManager,
            databaseKeys,
            context,
        )
        val evmAccountManager = accountManagerFactory.evmAccountManager(blockchainType, evmKitManager)

        val pair = Pair(evmKitManager, evmAccountManager)

        evmKitManagersMap[blockchainType] = pair

        return pair
    }

    fun getChain(blockchainType: BlockchainType) = when (blockchainType) {
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
        else -> throw IllegalArgumentException("Unsupported blockchain type $blockchainType")
    }

    fun getBlockchain(chainId: Int): Blockchain? =
        allBlockchains.firstOrNull { getChain(it.type).id == chainId }

    fun getBlockchain(token: Token): Blockchain? =
        allBlockchains.firstOrNull { token.blockchain == it }

    fun getBlockchain(blockchainType: BlockchainType): Blockchain? =
        allBlockchains.firstOrNull { it.type == blockchainType }

    fun getEvmKitManager(blockchainType: BlockchainType): EvmKitManager =
        getEvmKitManagers(blockchainType).first

    fun getEvmAccountManager(blockchainType: BlockchainType): EvmAccountManager =
        getEvmKitManagers(blockchainType).second

    /** Works without the key, so an account whose key is unreadable can still be deleted. */
    suspend fun clear(accountId: String) {
        blockchainTypes.mapNotNull(::getEvmKitManagerOrNull).forEach { it.stopFor(accountId) }
        blockchainTypes.map(::getChain).forEach { chain ->
            EthereumKit.clear(context, chain, accountId)
            Erc20Kit.clear(context, chain, accountId)
            MerkleTransactionAdapter.clear(context, chain, accountId)
            NftKit.clear(context, chain, accountId)
        }
        databaseKeys.remove(accountId)
    }

    fun getBaseToken(blockchainType: BlockchainType): Token? =
        marketKit.token(TokenQuery(blockchainType, TokenType.Native))

    companion object{
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
