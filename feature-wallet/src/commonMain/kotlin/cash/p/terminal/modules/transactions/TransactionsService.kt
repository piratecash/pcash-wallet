package cash.p.terminal.modules.transactions

import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.time.ZoneId

sealed interface SearchScanState {
    data object Idle : SearchScanState
    data object Scanning : SearchScanState
    data object Finished : SearchScanState

    /** The auto-scan window is spent and history remains; [shownCount] is what the screen reports. */
    data class Paused(val scannedCount: Int) : SearchScanState {
        val shownCount: Int
            get() = scannedCount.takeIf { it < SHOWN_COUNT_STEP }
                ?: (scannedCount / SHOWN_COUNT_STEP * SHOWN_COUNT_STEP)
    }
}

// A time-limited scan stops at an arbitrary count; the screen reports it rounded down.
private const val SHOWN_COUNT_STEP = 100

fun SearchScanState.forQuery(query: String): SearchScanState = if (query.isEmpty()) SearchScanState.Idle else this

/**
 * Until a search shows a row its scan shows only the spinner; shown rows stay while it scans further.
 * A new search resets the shown rows, so rows of a previous query never reappear.
 */
fun SearchScanState.showsOnlySpinner(query: String, shownRows: Map<String, List<TransactionViewItem>>?): Boolean =
    this == SearchScanState.Scanning && query.isNotEmpty() && shownRows.isNullOrEmpty()

fun SearchScanState.pausedCountText(numberFormatter: IAppNumberFormatter): String? =
    (this as? SearchScanState.Paused)?.let { numberFormatter.format(it.shownCount.toBigDecimal(), 0, 0) }

class TransactionsService(
    private val rateRepository: TransactionsRateRepository,
    private val transactionSyncStateRepository: TransactionSyncStateRepository,
    private val spamManager: SpamManager
) : Clearable {

    private val transactionRecordRepository: ITransactionRecordRepository by inject(
        ITransactionRecordRepository::class.java
    )

    private val _transactionItems = MutableStateFlow<List<TransactionItem>>(emptyList())
    val transactionItemsFlow: StateFlow<List<TransactionItem>> = _transactionItems.asStateFlow()

    private val _searchScanStateFlow = MutableStateFlow<SearchScanState>(SearchScanState.Idle)
    val searchScanStateFlow: StateFlow<SearchScanState> = _searchScanStateFlow.asStateFlow()

    private val _historyExhaustedFlow = MutableStateFlow(false)
    val historyExhaustedFlow: StateFlow<Boolean> = _historyExhaustedFlow.asStateFlow()

    val syncingFlow: StateFlow<Boolean> get() = transactionSyncStateRepository.syncingFlow

    @Volatile
    private var serviceVersion = 0
    private val searchPaging = SearchPaging()

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    fun start() {
        coroutineScope.launch {
            transactionRecordRepository.itemsFlow.collect(::handleUpdatedRecords)
        }
        coroutineScope.launch {
            rateRepository.dataExpiredFlow.collect {
                handleUpdatedHistoricalRates()
            }
        }
        coroutineScope.launch {
            rateRepository.historicalRateFlow.collect {
                handleUpdatedHistoricalRate(it.first, it.second)
            }
        }
        coroutineScope.launch {
            transactionSyncStateRepository.lastBlockInfoFlow
                .collect { (source, lastBlockInfo) ->
                    handleLastBlockInfo(source, lastBlockInfo)
                }
        }
    }

    fun set(
        transactionWallets: List<TransactionWallet>,
        transactionWallet: TransactionWallet?,
        filterTransactionType: FilterTransactionType,
        blockchain: Blockchain?,
        contact: Contact?,
        searchQuery: String? = null,
        period: TransactionPeriod? = null,
    ) {
        // Phase 1: apply state, learn whether a reload is needed — no emission, no load yet.
        val willReload = transactionRecordRepository.set(
            transactionWallets,
            transactionWallet,
            filterTransactionType,
            blockchain,
            contact,
            searchQuery,
            period?.timestampRange(ZoneId.systemDefault()),
        )

        if (willReload) {
            // Bump version, publish "scanning" and clear items BEFORE the scan emits anything,
            // so no stale "not found" frame can slip in ahead of the Scanning state.
            serviceVersion++
            searchPaging.reset()
            _searchScanStateFlow.value =
                if (searchQuery != null) SearchScanState.Scanning else SearchScanState.Idle
            _historyExhaustedFlow.value = false
            _transactionItems.value = emptyList()
            transactionRecordRepository.reloadItems()
        }

        transactionSyncStateRepository.setTransactionWallets(
            transactionWallets.filterBySelection(transactionWallet, blockchain)
        )
    }

    private fun handleLastBlockInfo(source: TransactionSource, lastBlockInfo: LastBlockInfo) {
        _transactionItems.update { currentList ->
            var updated = false
            val newList = currentList.map { item ->
                if (item.record.source == source && item.record.changedBy(item.lastBlockInfo, lastBlockInfo)) {
                    updated = true
                    item.withUpdatedListData(lastBlockInfo = lastBlockInfo)
                } else {
                    item
                }
            }
            if (updated) newList else currentList
        }
    }

    private fun handleUpdatedHistoricalRate(key: HistoricalRateKey, rate: CurrencyValue) {
        _transactionItems.update { currentList ->
            var updated = false
            val newList = currentList.map { item ->
                val currencyValue = item.record.currencyValue(key, rate)
                if (currencyValue != null) {
                    if (currencyValue == item.currencyValue) {
                        item
                    } else {
                        updated = true
                        item.withUpdatedListData(currencyValue = currencyValue)
                    }
                } else {
                    item
                }
            }
            if (updated) newList else currentList
        }
    }

    private fun handleUpdatedHistoricalRates() {
        _transactionItems.update { currentList ->
            var updated = false
            val newList = currentList.map { item ->
                val currencyValue = item.record.currencyValue(rateRepository)
                if (currencyValue == item.currencyValue) {
                    item
                } else {
                    updated = true
                    item.withUpdatedListData(currencyValue = currencyValue)
                }
            }

            if (updated) newList else currentList
        }
    }

    private suspend fun handleUpdatedRecords(batch: RecordsBatch) {
        val transactionRecords = batch.records
        val capturedVersion = serviceVersion

        // Discard stale data if set() or cancelPendingLoads() ran concurrently
        if (capturedVersion != serviceVersion) {
            return
        }

        _historyExhaustedFlow.value = batch.historyExhausted

        val currentItems = _transactionItems.value
        val newRecords = transactionRecords.filter { record ->
            currentItems.none { it.record == record }
        }

        if (batch.searchCompleted) {
            // A search batch is the whole current result list, so it replaces the published one.
            val state = searchPaging.searchStateFor(newRecords, batch, spamManager::shouldHide)
            searchPaging.publishInOrder(state, { setSearchState(it, capturedVersion) }) {
                publishRecords(transactionRecords, capturedVersion)
            }
            if (state == SearchScanState.Scanning) loadNext()
            return
        }

        when (searchPaging.actionFor(newRecords, spamManager::shouldHide)) {
            SearchPaging.Action.LoadNext -> return loadNext()
            SearchPaging.Action.Ignore -> return
            SearchPaging.Action.Publish -> publishRecords(transactionRecords, capturedVersion)
        }
    }

    private suspend fun publishRecords(transactionRecords: List<TransactionRecord>, capturedVersion: Int) {
        _transactionItems.update { latestItems ->
            // Re-check version inside CAS loop — if set() or cancelPendingLoads()
            // ran between the outer check and this point, discard stale data.
            if (capturedVersion != serviceVersion) return@update latestItems

            transactionRecords.mapNotNull { record ->
                val existingItem = latestItems.find { it.record == record }

                if (spamManager.shouldHide(record)) return@mapNotNull null

                if (existingItem == null) {
                    val lastBlockInfo = transactionSyncStateRepository.getLastBlockInfo(record.source)
                    val currencyValue = record.currencyValue(rateRepository)
                    TransactionItem(record, currencyValue, lastBlockInfo)
                } else if (existingItem.record === record) {
                    existingItem
                } else {
                    existingItem.withUpdatedListData(record = record)
                }
            }
        }
    }

    private fun setSearchState(state: SearchScanState, capturedVersion: Int) {
        if (capturedVersion == serviceVersion) _searchScanStateFlow.value = state
    }

    /** Scans the next auto-scan window after the search paused. */
    fun searchFurther() {
        searchPaging.openNextWindow()
        _searchScanStateFlow.value = SearchScanState.Scanning
        loadNext()
    }

    override fun clear() {
        coroutineScope.cancel()
        transactionRecordRepository.clear()
        rateRepository.clear()
        transactionSyncStateRepository.clear()
    }

    fun cancelPendingLoads() {
        serviceVersion++
        // Clear items to prevent always-on collectors (rates, metadata, block info)
        // from updating stale items after account switch.
        // The VM guards against the resulting Loading → Success(empty) flash.
        _searchScanStateFlow.value = SearchScanState.Idle
        _historyExhaustedFlow.value = false
        _transactionItems.value = emptyList()
        transactionRecordRepository.cancelPendingLoads()
    }

    suspend fun oldestTimestamp(): Long? = transactionRecordRepository.oldestTimestamp()

    fun reload() {
        coroutineScope.launch {
            transactionRecordRepository.reload()
        }
    }

    // A paused search reads nothing more until searchFurther().
    fun loadNext() {
        if (_searchScanStateFlow.value is SearchScanState.Paused) return
        coroutineScope.launch {
            transactionRecordRepository.loadNext()
        }
    }

    fun fetchRateIfNeeded(recordUid: String) {
        coroutineScope.launch {
            _transactionItems.value.find { it.record.uid == recordUid }?.let { transactionItem ->
                if (transactionItem.currencyValue == null) {
                    transactionItem.record.mainValue?.coin?.uid?.let { coinUid ->
                        rateRepository.fetchHistoricalRate(
                            HistoricalRateKey(
                                coinUid,
                                transactionItem.record.timestamp
                            )
                        )
                    }
                }
            }
        }
    }

    fun getTransactionItem(recordUid: String): TransactionItem? {
        return _transactionItems.value.find { it.record.uid == recordUid }
    }
}
