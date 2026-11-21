package cash.p.terminal.modules.transactions

import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.getShortOutgoingTransactionRecord

/** One rule for "the screen shows this row on this tab", so search keeps only rows the user can see. */
class TransactionSearchVisibility(
    private val spamManager: SpamManager,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val swapProviderRecordResolver: SwapProviderRecordResolver,
) {
    val hideSuspiciousTx: Boolean get() = spamManager.hideSuspiciousTx

    fun readsProviderSwaps(tab: FilterTransactionType): Boolean = tab == FilterTransactionType.Swap || tab.hidesSwaps

    /** [match] carries the provider swap when the matcher already resolved it, so it is not looked up twice. */
    fun isShown(
        record: TransactionRecord,
        tab: FilterTransactionType,
        extraSource: Boolean,
        match: TransactionSearchMatch,
    ): Boolean = when {
        spamManager.shouldHide(record) -> false
        extraSource -> isExtraSwapRecord(record)
        tab.hidesSwaps -> !record.isSwap() && (match.providerSwap ?: swapProviderRecordResolver.find(record)) == null
        else -> true
    }

    fun isExtraSwapRecord(record: TransactionRecord): Boolean =
        (record as? EvmTransactionRecord)?.swapDecodedFromCall == true ||
            // The provider fallback match treats SOLANA_INCOMING as outgoing, and this source reads All.
            (record.transactionRecordType != TransactionRecordType.SOLANA_INCOMING &&
                isMatchingSwapProviderTransaction(record))

    private fun isMatchingSwapProviderTransaction(record: TransactionRecord): Boolean {
        // First check by outgoingRecordUid (fast, already matched)
        swapProviderTransactionsStorage.getByOutgoingRecordUid(record.uid)?.let {
            return true
        }

        // Fall back to matching by token, amount and timestamp
        val shortOutgoingTransactionRecord = record.getShortOutgoingTransactionRecord()
            ?: return false

        val token = shortOutgoingTransactionRecord.token
            ?: return false

        return swapProviderTransactionsStorage.getByTokenIn(
            token = token,
            amountIn = shortOutgoingTransactionRecord.amountOut,
            timestamp = shortOutgoingTransactionRecord.timestamp
        ) != null
    }

    // Same rule as TransactionViewItem.isVisibleFor.
    private val FilterTransactionType.hidesSwaps: Boolean
        get() = this == FilterTransactionType.Incoming || this == FilterTransactionType.Outgoing
}

/** The complete acceptance test of one source's search; equal [key]s accept exactly the same records. */
class SourceSearch internal constructor(
    val key: Key,
    private val matcher: TransactionRecordSearchMatcher,
    private val visibility: TransactionSearchVisibility,
) {
    data class Key(
        val query: PreparedSearchQuery,
        val tab: FilterTransactionType,
        val extraSource: Boolean,
        val hideSuspiciousTx: Boolean,
        val sessionId: Long,
    )

    // The text match is cheap and runs first; only its hits reach the visibility rule.
    fun accepts(record: TransactionRecord): Boolean {
        val match = matcher.match(record, key.query) ?: return false
        return visibility.isShown(record, key.tab, key.extraSource, match)
    }
}
