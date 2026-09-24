package cash.p.terminal.core.managers

import android.content.Context
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.network.Network
import java.net.URL

// Single owner of the chain -> kit-network mapping. ThorchainKitManagers and the address layer
// (AddressHandlerThorchain) both derive their Network from here instead of repeating it.
val BlockchainType.thorchainNetwork: Network
    get() = when (this) {
        BlockchainType.Thorchain -> Network.Mainnet
        BlockchainType.Mayachain -> Network.MayaMainnet
        else -> throw IllegalArgumentException("No THORChain network for $uid")
    }

class ThorchainKitManagers(
    context: Context,
    kitDatabaseKeys: KitDatabaseKeys,
    backgroundManager: BackgroundManager,
    backgroundKeepAliveManager: BackgroundKeepAliveManager,
    networkErrorTracker: NetworkErrorTracker,
    offlineModeManager: OfflineModeManager,
) {
    val thorchain = ThorchainKitManager(
        BlockchainType.Thorchain.thorchainNetwork,
        BlockchainType.Thorchain,
        THORCHAIN_THORNODE_URLS,
        context,
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
        context,
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

    fun clear(accountId: String) = all.forEach { it.clear(accountId) }

    private companion object {
        // The kit fails over in list order.
        val THORCHAIN_THORNODE_URLS = listOf(
            URL("https://lcd-thorchain.keplr.app/"),
            URL("https://gateway.liquify.com/chain/thorchain_api/"),
        )
    }
}
