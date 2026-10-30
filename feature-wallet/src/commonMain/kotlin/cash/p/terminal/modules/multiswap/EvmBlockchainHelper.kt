package cash.p.terminal.modules.multiswap

import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmSyncSourceManager

class EvmBlockchainHelper(private val blockchainType: BlockchainType) {
    val chain by lazy { getKoinInstance<EvmBlockchainManager>().getChain(blockchainType) }

    fun getRpcSourceHttp(): RpcSource.Http {
        val httpSyncSource = getKoinInstance<EvmSyncSourceManager>().getHttpSyncSource(blockchainType)
        return httpSyncSource?.rpcSource as? RpcSource.Http
            ?: throw IllegalStateException("No HTTP RPC Source for blockchain $blockchainType")
    }

}
