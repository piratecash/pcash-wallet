package cash.p.terminal.modules.syncerror

import io.horizontalsystems.core.entities.Blockchain

object SyncErrorModule {

    data class BlockchainWrapper(
        val blockchain: Blockchain,
        val type: Type
    ) {
        enum class Type {
            Bitcoin, Evm
        }
    }
}
