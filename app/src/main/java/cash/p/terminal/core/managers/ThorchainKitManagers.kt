package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.entities.BlockchainType

class ThorchainKitManagers(
    context: Context,
    kitDatabaseKeys: BitcoinKitDatabaseKeyProvider,
    backgroundManager: BackgroundManager,
    backgroundKeepAliveManager: BackgroundKeepAliveManager,
    networkErrorTracker: NetworkErrorTracker,
    offlineModeManager: OfflineModeManager,
    hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    deviceSigners: DeviceSignerFactory,
) {
    val thorchain = ThorchainKitManager(
        BlockchainType.Thorchain,
        context,
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
        context,
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

    fun clear(accountId: String) = all.forEach { it.clear(accountId) }
}
