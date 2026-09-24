package cash.p.terminal.core.notifications.polling

import cash.p.terminal.core.managers.ThorchainKitManagers
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class ThorchainTransactionsPoller(
    private val thorchainKitManagers: ThorchainKitManagers,
    private val transactionAdapterManager: TransactionAdapterManager,
) : TransactionsPoller {

    override val blockchainTypes = setOf(BlockchainType.Thorchain, BlockchainType.Mayachain)

    override suspend fun pollOnce(wallets: List<Wallet>): List<TransactionRecord> = coroutineScope {
        wallets.groupBy { it.token.blockchainType }.map { (blockchainType, chainWallets) ->
            async {
                val kitManager = thorchainKitManagers.forType(blockchainType)

                withTimeoutOrNull(TransactionsPoller.POLLING_TIMEOUT_MS) {
                    kitManager.startForPolling()
                    try {
                        awaitSyncAndRead(chainWallets, transactionAdapterManager)
                    } finally {
                        // The timeout already cancelled this scope; a contended mutex would drop the session release.
                        withContext(NonCancellable) { kitManager.stopForPolling() }
                    }
                } ?: emptyList<TransactionRecord>().also {
                    Timber.tag("TxPoller").w("%s poll timed out", blockchainType.uid)
                }
            }
        }.awaitAll().flatten()
    }
}
