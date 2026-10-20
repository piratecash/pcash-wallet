package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.core.factories.EvmAccountManagerFactory
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.merkleiokit.MerkleTransactionAdapter
import io.horizontalsystems.nftkit.core.NftKit
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.MarketKitWrapper

class EvmBlockchainManager(
    private val backgroundManager: BackgroundManager,
    private val syncSourceManager: EvmSyncSourceManager,
    marketKit: MarketKitWrapper,
    private val accountManagerFactory: EvmAccountManagerFactory,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val networkErrorTracker: NetworkErrorTracker,
    private val offlineModeManager: OfflineModeManager,
    private val databaseKeys: EvmKitDatabaseKeyProvider,
    private val context: Context,
) : EvmBlockchains by EvmBlockchainCatalog(marketKit) {
    private val evmKitManagersMap = mutableMapOf<BlockchainType, Pair<EvmKitManager, EvmAccountManager>>()

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

    companion object {
        val blockchainTypes = EvmBlockchains.blockchainTypes
    }
}
