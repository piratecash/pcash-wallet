package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.zcash.Transaction
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/** A negative value means the account spent more than it received here. */
val Transaction.isIncoming: Boolean
    get() = value >= 0

class ZcashTransactionsProvider {

    private val mutex = Mutex()
    private var transactions = listOf<Transaction>()
    private val newTransactionsFlow = MutableSharedFlow<List<Transaction>>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val reloadSignalFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a transaction is gone, which the content flow below cannot express. */
    val transactionsReloadSignalFlow: SharedFlow<Unit> = reloadSignalFlow.asSharedFlow()

    /**
     * The session publishes its whole history, so the list is replaced rather than merged: an
     * unconfirmed transaction that expired or got mined has to disappear. A transaction already
     * known can come back mined, with a fee or a recipient it did not have before — such an
     * update is republished.
     */
    suspend fun onTransactions(all: List<Transaction>) {
        val (updated, removed) = mutex.withLock {
            val known = transactions.associateBy { it.txid }
            val updated = all.filter { known[it.txid] != it }
            val currentIds = all.mapTo(mutableSetOf()) { it.txid }
            val removed = known.keys.any { it !in currentIds }
            transactions = all.sortedWith(ORDER)
            updated to removed
        }

        if (updated.isNotEmpty()) {
            newTransactionsFlow.emit(updated)
        }
        if (removed) {
            reloadSignalFlow.emit(Unit)
        }
    }

    fun getNewTransactionsFlowable(
        transactionType: FilterTransactionType,
        address: String?
    ): Flow<List<Transaction>> {
        val filters = getFilters(transactionType, address)

        return if (filters.isEmpty()) {
            newTransactionsFlow
        } else {
            newTransactionsFlow.map { txs ->
                txs.filter { tx ->
                    filters.all { filter -> filter.invoke(tx) }
                }
            }.filter {
                it.isNotEmpty()
            }
        }
    }

    private fun getFilters(
        transactionType: FilterTransactionType,
        address: String?,
    ) = buildList<(Transaction) -> Boolean> {
        when (transactionType) {
            FilterTransactionType.All -> Unit
            // For Incoming, exclude change transactions - they are part of outgoing transactions
            FilterTransactionType.Incoming -> add { it.isIncoming && !it.isChange }
            FilterTransactionType.Outgoing -> add { !it.isIncoming }
            FilterTransactionType.Swap,
            FilterTransactionType.Approve,
                -> add { false }
        }

        if (address != null) {
            add {
                it.recipient.equals(address, ignoreCase = true)
            }
        }
    }

    fun getTransactions(
        from: Triple<String, Long, Int>?,
        transactionType: FilterTransactionType,
        address: String?,
        limit: Int,
    ): List<Transaction> = try {
        val filters = getFilters(transactionType, address)
        val filtered = when {
            filters.isEmpty() -> transactions
            else -> transactions.filter { tx -> filters.all { it.invoke(tx) } }
        }

        val fromIndex = from?.let { (txid, time, id) ->
            filtered.indexOfFirst { it.txid == txid && it.time == time && it.id == id } + 1
        } ?: 0

        filtered.subList(fromIndex, min(filtered.size, fromIndex + limit))
    } catch (error: Throwable) {
        emptyList()
    }

    private companion object {
        val ORDER = compareByDescending<Transaction> { it.time }.thenByDescending { it.id }
    }
}
