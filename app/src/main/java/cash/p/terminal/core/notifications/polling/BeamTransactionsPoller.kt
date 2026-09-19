package cash.p.terminal.core.notifications.polling

import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

class BeamTransactionsPoller(
    private val transactionAdapterManager: TransactionAdapterManager,
) : TransactionsPoller {
    override val blockchainTypes = setOf(BlockchainType.Beam)

    override suspend fun pollOnce(wallets: List<Wallet>): List<TransactionRecord> =
        withTimeoutOrNull(TransactionsPoller.POLLING_TIMEOUT_MS) {
            wallets.filter { it.token.blockchainType == BlockchainType.Beam }
                .distinctBy { it.transactionSource }
                .flatMap { wallet ->
                    val adapter = withTimeoutOrNull(ADAPTER_WAIT_MS) {
                        transactionAdapterManager.adaptersReadyFlow
                            .map { it[wallet.transactionSource] as? BeamAdapter }
                            .first { it?.accountId == wallet.account.id }
                    }
                    adapter?.pollTransactions().orEmpty()
                }
        } ?: emptyList()

    private companion object {
        const val ADAPTER_WAIT_MS = 10_000L
    }
}
