package cash.p.terminal.modules.transactions

import cash.p.terminal.wallet.Clearable
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.contacts.model.Contact
import io.horizontalsystems.core.entities.Blockchain
import kotlinx.coroutines.flow.SharedFlow

interface ITransactionRecordRepository : Clearable {
    val itemsFlow: SharedFlow<RecordsBatch>

    /**
     * Phase 1: applies wallets/filters and reports whether a reload is required.
     * Does NOT start loading — call [reloadItems] afterwards. Splitting load from
     * state mutation lets the caller publish "scanning" state before any emission.
     */
    fun set(
        transactionWallets: List<TransactionWallet>,
        wallet: TransactionWallet?,
        transactionType: FilterTransactionType,
        blockchain: Blockchain?,
        contact: Contact?,
        searchQuery: String? = null,
    ): Boolean

    /** Phase 2: starts loading page 1 with the state applied by [set]. */
    fun reloadItems()
    fun loadNext()
    fun reload()
    fun cancelPendingLoads()
}

/**
 * One emission of the records flow.
 * @param searchCompleted true only on the terminal emission of a search scan.
 * @param searchExhausted true when the scan exhausted the source (a further loadNext would be a no-op).
 * @param loadFailed true when the batch is empty only because a source did not answer - an empty
 *   batch with this flag unset is a trustworthy "no transactions".
 * @param searchScannedCount records this search has scanned so far, summed over sources.
 * @param searchSessionId changes whenever the search restarts from its first page.
 * @param searchTimedOut true when the scan step stopped on its time limit or a source did not answer.
 * @param searchRefresh true when an update re-read an already published search page.
 */
data class RecordsBatch(
    val records: List<TransactionRecord>,
    val searchCompleted: Boolean = false,
    val searchExhausted: Boolean = false,
    val loadFailed: Boolean = false,
    val searchScannedCount: Int = 0,
    val searchSessionId: Long = 0,
    val searchTimedOut: Boolean = false,
    val searchRefresh: Boolean = false,
)
