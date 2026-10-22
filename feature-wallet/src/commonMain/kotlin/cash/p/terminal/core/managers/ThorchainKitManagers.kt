package cash.p.terminal.core.managers

import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.network.Network
import java.net.URL

class ThorchainKitManagers(
    platformContexts: KitPlatformContexts,
    kitDatabaseKeys: BitcoinKitDatabaseKeyProvider,
    backgroundManager: IBackgroundManager,
    backgroundKeepAliveManager: BackgroundKeepAliveManager,
    networkErrorTracker: NetworkErrorTracker,
    offlineModeManager: OfflineModeManager,
) {
    val thorchain = ThorchainKitManager(
        BlockchainType.Thorchain.thorchainNetwork,
        BlockchainType.Thorchain,
        THORCHAIN_THORNODE_URLS,
        platformContexts,
        kitDatabaseKeys,
        backgroundManager,
        backgroundKeepAliveManager,
        networkErrorTracker,
        offlineModeManager,
    )

    val maya = ThorchainKitManager(
        BlockchainType.Mayachain.thorchainNetwork,
        BlockchainType.Mayachain,
        Network.MayaMainnet.thornodeUrls,
        platformContexts,
        kitDatabaseKeys,
        backgroundManager,
        backgroundKeepAliveManager,
        networkErrorTracker,
        offlineModeManager,
    )

    val all = listOf(thorchain, maya)

    fun forType(blockchainType: BlockchainType): ThorchainKitManager = when (blockchainType) {
        BlockchainType.Thorchain -> thorchain
        BlockchainType.Mayachain -> maya
        else -> throw IllegalArgumentException("No THORChain kit for ${blockchainType.uid}")
    }

    suspend fun clear(accountId: String) = all.forEach { it.clear(accountId) }

    private companion object {
        // The kit fails over in list order.
        val THORCHAIN_THORNODE_URLS = listOf(
            URL("https://lcd-thorchain.keplr.app/"),
            URL("https://gateway.liquify.com/chain/thorchain_api/"),
        )
    }
}
