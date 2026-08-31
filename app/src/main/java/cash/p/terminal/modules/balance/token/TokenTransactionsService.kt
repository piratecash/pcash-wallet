package cash.p.terminal.modules.balance.token

import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.nft.NftAssetBriefMetadata
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.nftUids
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.HistoricalRateKey
import cash.p.terminal.modules.transactions.ITransactionRecordRepository
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.SearchScanState
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionSyncStateRepository
import cash.p.terminal.modules.transactions.TransactionWallet
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.modules.transactions.currencyValue
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.Executors

class TokenTransactionsService(
    private val wallet: Wallet,
    private val rateRepository: TransactionsRateRepository,
    private val transactionSyncStateRepository: TransactionSyncStateRepository,
    private val transactionAdapterManager: TransactionAdapterManager,
    private val nftMetadataService: NftMetadataService,
    private val spamManager: SpamManager,
) : Clearable {
    private enum class RecordsLoadFailure {
        AdapterUnavailable,
        RepositoryRead,
    }

    private val transactionRecordRepository: ITransactionRecordRepository by inject(
        ITransactionRecordRepository::class.java
    )
    private val adapterManager: IAdapterManager by inject(IAdapterManager::class.java)
    private val _transactionItems = MutableStateFlow<List<TransactionItem>>(emptyList())
    val transactionItemsFlow: StateFlow<List<TransactionItem>> = _transactionItems.asStateFlow()
    private val _recordsLoadedFlow = MutableStateFlow(false)
    val recordsLoadedFlow: StateFlow<Boolean> = _recordsLoadedFlow.asStateFlow()
    private val _recordsLoadFailedFlow = MutableStateFlow(false)
    val recordsLoadFailedFlow: StateFlow<Boolean> = _recordsLoadFailedFlow.asStateFlow()
    private val recordsLoadFailureLock = Any()
    private var recordsLoadFailure: RecordsLoadFailure? = null
    val syncingFlow: StateFlow<Boolean> = transactionSyncStateRepository.syncingFlow
    private val _searchScanStateFlow = MutableStateFlow(SearchScanState.Idle)
    val searchScanStateFlow: StateFlow<SearchScanState> = _searchScanStateFlow.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var lastHiddenOnlyLoadKey: String? = null
    private var emptyBatchFallbackJob: Job? = null

    @Volatile
    private var serviceVersion = 0

    @Volatile
    private var initialClearPending = true

    @Volatile
    private var selectedTransactionType: FilterTransactionType = FilterTransactionType.All

    @Volatile
    private var searchQuery: String? = null

    @Volatile
    private var initialized = false

    private val transactionWallet by lazy {
        TransactionWallet(wallet.token, wallet.transactionSource, wallet.badge)
    }

    fun start() {
        coroutineScope.launch {
            transactionRecordRepository.itemsFlow.collect {
                handleUpdatedRecords(
                    transactionRecords = it.records,
                    searchCompleted = it.searchCompleted,
                    searchExhausted = it.searchExhausted,
                    loadFailed = it.loadFailed,
                )
            }
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
        coroutineScope.launch {
            nftMetadataService.assetsBriefMetadataFlow.collect {
                handle(it)
            }
        }
        coroutineScope.launch {
            // Completion is emitted only after TransactionAdapterManager has published its final
            // adapter map. Until then a missing adapter is merely slow, not a failed history read.
            transactionAdapterManager.initializationFlow
                .collect { initialized ->
                    updateAdapterUnavailableFailure(initialized)
                }
        }
        coroutineScope.launch {
            // Wait for this wallet's specific adapter to be ready rather than
            // relying on initializationFlow, which may fire before all adapters
            // are in the map due to partial-batch emissions from AdapterManager.
            // distinctUntilChanged runs before filterNotNull so the gap left by a removed adapter
            // is still observed: the same instance coming back is a restore, not a duplicate.
            // Slow adapter creation is not a failed history read. The repository reports a real
            // read failure after an adapter becomes available and a load is actually attempted.
            // Collected directly: no operator buffers the hand-off, so the collector is never
            // woken for an adapter the manager has already replaced. An adapter that still
            // disappears before the repository resolves it yields a failed regular read, not an
            // empty history. The collector never ends, so the screen recovers once it reappears.
            transactionAdapterManager.adaptersReadyFlow
                .map { it[wallet.transactionSource] }
                .distinctUntilChanged()
                .filterNotNull()
                .collect {
                    clearAdapterUnavailableFailure()
                    handleInitialization()
                }
        }
    }

    private fun handleInitialization() {
        lastHiddenOnlyLoadKey = null
        transactionSyncStateRepository.setTransactionWallets(listOf(transactionWallet))
        val loadedType = selectedTransactionType
        val loadedSearchQuery = searchQuery
        loadTransactions()
        initialized = true
        // A setTransactionType() or setSearchQuery() that raced during the initial load saw
        // initialized == false and skipped its own reload; apply the latest selection if it
        // changed meanwhile.
        if (selectedTransactionType != loadedType || searchQuery != loadedSearchQuery) {
            loadTransactions()
        }
    }

    private fun loadTransactions() {
        val willReload = transactionRecordRepository.set(
            transactionWallets = listOf(transactionWallet),
            wallet = transactionWallet,
            transactionType = selectedTransactionType,
            blockchain = null,
            contact = null,
            searchQuery = searchQuery,
        )
        // Only a real reload may reopen the loading window: without a batch to close it again the
        // screen would keep already loaded records behind the sync placeholder indefinitely.
        if (!willReload) return
        openLoadingWindow()
        transactionRecordRepository.reloadItems()
    }

    /** A new selection invalidates the visible list; a pending fallback must not close it. */
    private fun dropToLoading() {
        emptyBatchFallbackJob?.cancel()
        _recordsLoadedFlow.value = false
    }

    /** A real read is starting: the previous read's failure no longer describes the screen. */
    private fun openLoadingWindow() {
        dropToLoading()
        setRecordsLoadFailure(null)
    }

    private fun markRecordsLoaded() {
        _recordsLoadedFlow.value = true
        setRecordsLoadFailure(null)
    }

    private fun setRecordsLoadFailure(failure: RecordsLoadFailure?) =
        synchronized(recordsLoadFailureLock) {
            recordsLoadFailure = failure
            _recordsLoadFailedFlow.value = failure != null
        }

    private fun updateAdapterUnavailableFailure(initialized: Boolean) =
        synchronized(recordsLoadFailureLock) {
            val adapterUnavailable = initialized &&
                transactionAdapterManager.getAdapter(wallet.transactionSource) == null
            if (recordsLoadFailure != RecordsLoadFailure.RepositoryRead) {
                recordsLoadFailure = RecordsLoadFailure.AdapterUnavailable.takeIf { adapterUnavailable }
                _recordsLoadFailedFlow.value = adapterUnavailable
            }
        }

    private fun clearAdapterUnavailableFailure() = synchronized(recordsLoadFailureLock) {
        if (recordsLoadFailure == RecordsLoadFailure.AdapterUnavailable) {
            recordsLoadFailure = null
            _recordsLoadFailedFlow.value = false
        }
    }

    fun setTransactionType(transactionType: FilterTransactionType) {
        // Bump version so an in-flight handleUpdatedRecords() of the previous filter is
        // discarded, then drop to loading and clear the previous-filter list before reload.
        // The repository's type-change branch does not emit emptyList(), so without this the
        // stale list of the old type stays visible until the first filtered page arrives.
        selectedTransactionType = transactionType
        serviceVersion++
        dropToLoading()
        // Set before clearing the list: a search re-scan is driven by the resulting reload, so
        // the scan state must already read Scanning by the time collectors see the empty list -
        // otherwise a stale Finished/Idle could slip through and flash the old results as final.
        _searchScanStateFlow.value = if (searchQuery != null) SearchScanState.Scanning else SearchScanState.Idle
        _transactionItems.value = emptyList()
        // Before initialization the repository has no adapters yet, so set() would load against an
        // empty adapter map and emit a premature empty result that flips recordsLoaded to true,
        // flashing "no transactions". handleInitialization() issues the first set() with the saved
        // type once adapters are ready.
        if (initialized) {
            loadTransactions()
        }
    }

    /**
     * Applies (or clears, for `null`) an in-app search query, preserving the current
     * [selectedTransactionType] and wallet. Mirrors [setTransactionType]'s reset pattern; the
     * resulting reload is driven deeper by [handleUpdatedRecords] calling [loadNext] while the
     * repository reports the scan is not yet exhausted.
     */
    fun setSearchQuery(query: String?) {
        searchQuery = query
        serviceVersion++
        dropToLoading()
        lastHiddenOnlyLoadKey = null
        _searchScanStateFlow.value = if (query != null) SearchScanState.Scanning else SearchScanState.Idle
        _transactionItems.value = emptyList()
        if (initialized) {
            loadTransactions()
        }
    }

    /**
     * The swallowed batch may have been the wallet's real (empty) history, in which case nothing
     * follows it: release the screen instead of blocking it on a page that never arrives.
     */
    private fun scheduleEmptyBatchFallback() {
        emptyBatchFallbackJob?.cancel()
        emptyBatchFallbackJob = coroutineScope.launch {
            delay(EMPTY_BATCH_FALLBACK_MS)
            markRecordsLoaded()
        }
    }

    private fun handle(assetBriefMetadataMap: Map<NftUid, NftAssetBriefMetadata>) {
        _transactionItems.update { currentList ->
            if (currentList.isEmpty()) return@update currentList

            var updated = false
            val newList = currentList.map { item ->
                val updatedMetadata = item.nftMetadata.toMutableMap()
                item.record.nftUids.forEach { nftUid ->
                    assetBriefMetadataMap[nftUid]?.let { updatedMetadata[nftUid] = it }
                }

                if (updatedMetadata == item.nftMetadata) {
                    item
                } else {
                    updated = true
                    item.withUpdatedListData(nftMetadata = updatedMetadata)
                }
            }

            if (updated) newList else currentList
        }
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

    private suspend fun handleUpdatedRecords(
        transactionRecords: List<TransactionRecord>,
        searchCompleted: Boolean,
        searchExhausted: Boolean,
        loadFailed: Boolean,
    ) {
        // A source did not answer, so this empty batch is not a verdict about the wallet: keep the
        // published items and report the failure. The pending initial clear is consumed here so a
        // later successful empty batch still takes the normal path and releases the screen.
        if (loadFailed) {
            initialClearPending = false
            emptyBatchFallbackJob?.cancel()
            setRecordsLoadFailure(RecordsLoadFailure.RepositoryRead)
            return
        }
        // The repository emits a synthetic empty list once, when wallets are first set, to clear the
        // UI before the initial page loads. That clear is not a completed load: marking records as
        // loaded on it would let an already-synced wallet briefly show "no transactions" before its
        // first page arrives. Drop it once; a genuinely empty wallet still reports loaded through the
        // real (also empty) result that follows. A terminal empty SEARCH batch (searchCompleted) is
        // never that synthetic clear - it is the real (empty) search result, so let it flow through
        // instead of leaving the UI stuck on the scanning spinner.
        if (initialClearPending) {
            initialClearPending = false
            if (transactionRecords.isEmpty() && !searchCompleted) {
                scheduleEmptyBatchFallback()
                return
            }
        }
        emptyBatchFallbackJob?.cancel()

        val capturedVersion = serviceVersion
        val nftUids = transactionRecords.nftUids
        val nftMetadata = nftMetadataService.assetsBriefMetadata(nftUids)

        val missingNftUids = nftUids.subtract(nftMetadata.keys)
        if (missingNftUids.isNotEmpty()) {
            coroutineScope.launch {
                nftMetadataService.fetch(missingNftUids)
            }
        }

        // Discard a stale batch if setTransactionType() ran concurrently while loading metadata.
        if (capturedVersion != serviceVersion) return

        val currentItems = _transactionItems.value
        val newRecords = transactionRecords.filter { record ->
            currentItems.none { it.record == record }
        }

        if (newRecords.isNotEmpty() && newRecords.all { spamManager.shouldHide(it) }) {
            markRecordsLoaded()
            handleAllSpamPage(newRecords, searchCompleted, searchExhausted, capturedVersion)
            return
        }

        lastHiddenOnlyLoadKey = null

        publishRecords(transactionRecords, nftMetadata, capturedVersion)

        // Mark loaded only after the items are published, so a consumer that observes
        // syncing flip to false always sees the new list — never an empty intermediate state.
        markRecordsLoaded()
        // A search batch is always the terminal answer for its requested page; flip out of
        // Scanning so the UI shows the (possibly empty) results instead of a spinner.
        if (searchCompleted && capturedVersion == serviceVersion) {
            _searchScanStateFlow.value = SearchScanState.Finished
        }
    }

    private suspend fun publishRecords(
        transactionRecords: List<TransactionRecord>,
        nftMetadata: Map<NftUid, NftAssetBriefMetadata>,
        capturedVersion: Int,
    ) {
        _transactionItems.update { latestItems ->
            // Re-check inside the CAS loop: setTransactionType() may have run between the
            // outer check and here.
            if (capturedVersion != serviceVersion) return@update latestItems

            transactionRecords.mapNotNull { record ->
                val existingItem = latestItems.find { it.record == record }

                if (spamManager.shouldHide(record)) return@mapNotNull null

                if (existingItem == null) {
                    val lastBlockInfo = transactionSyncStateRepository.getLastBlockInfo(record.source)
                    val currencyValue = record.currencyValue(rateRepository)
                    TransactionItem(record, currencyValue, lastBlockInfo, nftMetadata)
                } else if (existingItem.record === record) {
                    existingItem
                } else {
                    existingItem.withUpdatedListData(record = record)
                }
            }
        }
    }

    /**
     * An all-spam page never contains a real match. Page deeper while the repository reports the
     * scan is not yet exhausted (deduplicated via [lastHiddenOnlyLoadKey] so a repeated identical
     * page doesn't re-trigger [loadNext]); once exhausted, this all-spam page is the final answer.
     */
    private fun handleAllSpamPage(
        newRecords: List<TransactionRecord>,
        searchCompleted: Boolean,
        searchExhausted: Boolean,
        capturedVersion: Int,
    ) {
        val canPageDeeper = !searchCompleted || !searchExhausted
        if (canPageDeeper) {
            val hiddenOnlyLoadKey = newRecords.joinToString(separator = "|") { it.uid }
            if (lastHiddenOnlyLoadKey != hiddenOnlyLoadKey) {
                lastHiddenOnlyLoadKey = hiddenOnlyLoadKey
                loadNext()
            }
        } else if (capturedVersion == serviceVersion) {
            _searchScanStateFlow.value = SearchScanState.Finished
        }
    }

    private val executorService = Executors.newCachedThreadPool()

    fun refreshList() {
        // Original behavior:
        // - forceLoadData=true: copy items (new references), then emit
        // - forceLoadData=false: emit same items (same references)
        //
        // With StateFlow, emission only happens if value changes (structural equality).
        // To preserve the "always emit" behavior for callers that depend on it (e.g.,
        // when cache/visibility changes), we always copy items. This changes referential
        // identity when forceLoadData=false, but Compose uses structural equality anyway.
        _transactionItems.update { currentList ->
            if (currentList.isEmpty()) return@update currentList
            currentList.map { it.copy() }
        }
    }

    fun reload() {
        if (!initialized || transactionAdapterManager.getAdapter(wallet.transactionSource) == null) {
            // No transaction adapter: a repository read would run against an empty adapter map and
            // answer "no transactions", and the repository can only rebuild it on set(), not on
            // reload(). Asking for the adapter back is the real retry. The adapter can also be gone
            // after initialization, when it disappeared between the emission and the repository
            // resolving it.
            adapterManager.refreshAdapters(listOf(wallet))
            return
        }
        openLoadingWindow()
        transactionRecordRepository.reload()
    }

    fun loadNext() {
        executorService.submit {
            transactionRecordRepository.loadNext()
        }
    }

    fun fetchRateIfNeeded(recordUid: String) {
        executorService.submit {
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


    override fun clear() {
        transactionRecordRepository.clear()
        rateRepository.clear()
        transactionSyncStateRepository.clear()
        coroutineScope.cancel()
        executorService.shutdown()
    }

    private companion object {
        const val EMPTY_BATCH_FALLBACK_MS = 500L
    }
}
