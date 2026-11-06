package cash.p.terminal.modules.swapprogress

import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.UnstoppableChainIds
import cash.p.terminal.modules.transactionInfo.TransactionInfoModule

class SwapLegExplorer(private val transactionAdapterManager: TransactionAdapterManager) {

    fun explorer(chainId: String?, accountId: String, hash: String): TransactionInfoModule.ExplorerData? {
        val blockchainType = UnstoppableChainIds.blockchainType(chainId) ?: return null
        val adapter = transactionAdapterManager.adaptersReadyFlow.value.entries
            .firstOrNull { (source, _) ->
                source.blockchain.type == blockchainType && source.account.id == accountId
            }?.value ?: return null
        val url = adapter.getTransactionUrl(hash).takeIf { it.isNotBlank() } ?: return null
        return TransactionInfoModule.ExplorerData(adapter.explorerTitle, url)
    }
}
