package cash.p.terminal.core.managers

import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.entities.BlockchainType

class ThorchainKitManagers(
    platformContexts: KitPlatformContexts,
    kitDatabaseKeys: BitcoinKitDatabaseKeyProvider,
    backgroundManager: IBackgroundManager,
    backgroundKeepAliveManager: BackgroundKeepAliveManager,
    networkErrorTracker: NetworkErrorTracker,
    offlineModeManager: OfflineModeManager,
    hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    deviceSigners: DeviceSignerFactory,
) {
    val thorchain = ThorchainKitManager(
        BlockchainType.Thorchain,
        platformContexts,
        kitDatabaseKeys,
        backgroundManager,
        backgroundKeepAliveManager,
        networkErrorTracker,
        offlineModeManager,
        hardwarePublicKeyStorage,
        deviceSigners,
    )

    val maya = ThorchainKitManager(
        BlockchainType.Mayachain,
        platformContexts,
        kitDatabaseKeys,
        backgroundManager,
        backgroundKeepAliveManager,
        networkErrorTracker,
        offlineModeManager,
        hardwarePublicKeyStorage,
        deviceSigners,
    )

    val all = listOf(thorchain, maya)

    fun forType(blockchainType: BlockchainType): ThorchainKitManager = when (blockchainType) {
        BlockchainType.Thorchain -> thorchain
        BlockchainType.Mayachain -> maya
        else -> throw IllegalArgumentException("No THORChain kit for ${blockchainType.uid}")
    }

    suspend fun clear(accountId: String) = all.forEach { it.clear(accountId) }
}
