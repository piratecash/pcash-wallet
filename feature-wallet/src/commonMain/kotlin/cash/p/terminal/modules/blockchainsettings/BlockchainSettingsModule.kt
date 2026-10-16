package cash.p.terminal.modules.blockchainsettings

import cash.p.terminal.core.managers.ZcashServer
import cash.p.terminal.core.order
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.entities.EvmSyncSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.solanakit.models.RpcSource

object BlockchainSettingsModule {

    val statusOnlyBlockchainTypes = listOf(
        BlockchainType.Beam,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Monero,
        BlockchainType.Stellar,
        BlockchainType.Thorchain,
        BlockchainType.Mayachain,
    )

    data class BlockchainViewItem(
        val title: String,
        val subtitle: String,
        val imageUrl: String,
        val blockchainItem: BlockchainItem
    )

    sealed class BlockchainItem {
        abstract val blockchain: Blockchain

        class Btc(
            override val blockchain: Blockchain,
            val restoreMode: BtcRestoreMode
        ) : BlockchainItem()

        class Evm(
            override val blockchain: Blockchain,
            val syncSource: EvmSyncSource
        ) : BlockchainItem()

        class Solana(
            override val blockchain: Blockchain,
            val rpcSource: RpcSource
        ) : BlockchainItem()

        class Zcash(
            override val blockchain: Blockchain,
            val server: ZcashServer,
            val auto: Boolean
        ) : BlockchainItem()

        class StatusOnly(
            override val blockchain: Blockchain
        ) : BlockchainItem()

        val order
            get() = blockchain.type.order
    }

}
