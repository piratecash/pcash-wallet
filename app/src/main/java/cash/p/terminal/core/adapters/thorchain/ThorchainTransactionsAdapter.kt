package cash.p.terminal.core.adapters.thorchain

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.factories.ThorchainTransactionConverter
import cash.p.terminal.core.managers.ThorchainKitWrapper
import cash.p.terminal.core.managers.toAdapterState
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Token
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.Network
import io.reactivex.Flowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx2.asFlowable

class ThorchainTransactionsAdapter(
    thorchainKitWrapper: ThorchainKitWrapper,
    private val transactionConverter: ThorchainTransactionConverter,
) : ITransactionsAdapter {
    private val kit = thorchainKitWrapper.thorchainKit
    private val isMaya = kit.network == Network.MayaMainnet

    override val explorerTitle = if (isMaya) "MayaScan" else "RuneScan"

    override val transactionsState: AdapterState
        get() = kit.transactionsSyncStateFlow.value.toAdapterState()

    override val transactionsStateUpdatedFlowable: Flowable<Unit>
        get() = kit.transactionsSyncStateFlow.asFlowable().map { }

    override val lastBlockInfo: LastBlockInfo?
        get() = kit.lastBlockHeight.takeIf { it > 0 }?.let { LastBlockInfo(it.toInt()) }

    override val lastBlockUpdatedFlowable: Flowable<Unit>
        get() = kit.lastBlockHeightFlow.asFlowable().map { }

    // One transaction yields several records, so paging resumes after `from` itself rather than
    // after its timestamp: records sharing that second would otherwise be skipped.
    override suspend fun getTransactions(
        from: TransactionRecord?,
        token: Token?,
        limit: Int,
        transactionType: FilterTransactionType,
        address: String?,
    ): List<TransactionRecord> {
        val records = kit.getTransactions(fromTimestamp = from?.timestamp?.plus(1))
            .toRecords(token, transactionType, address)
        val resumeIndex = records.indexOfFirst { it.uid == from?.uid } + 1
        return records.drop(resumeIndex).take(limit)
    }

    override fun getTransactionRecordsFlow(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): Flow<List<TransactionRecord>> =
        kit.transactionsFlow.map { it.toRecords(token, transactionType, address) }

    override fun getTransactionUrl(transactionHash: String): String =
        if (isMaya) {
            "https://www.mayascan.org/tx/$transactionHash"
        } else {
            "https://runescan.io/tx/$transactionHash"
        }

    private fun List<Transaction>.toRecords(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): List<ThorchainTransactionRecord> =
        flatMap(transactionConverter::convert)
            .filter { it.matches(token, transactionType, address) }

    private fun ThorchainTransactionRecord.matches(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): Boolean {
        val typeMatches = when (transactionType) {
            FilterTransactionType.All -> true
            FilterTransactionType.Incoming -> type is ThorchainTransactionRecord.Type.Incoming
            FilterTransactionType.Outgoing -> type is ThorchainTransactionRecord.Type.Outgoing
            FilterTransactionType.Swap,
            FilterTransactionType.Approve -> false
        }
        val tokenMatches = token == null || (mainValue as? TransactionValue.CoinValue)?.token == token
        val counterparty = from ?: to?.firstOrNull()
        val addressMatches = address == null || counterparty.equals(address, ignoreCase = true)
        return typeMatches && tokenMatches && addressMatches
    }
}
