package cash.p.terminal.modules.transactions

import cash.p.terminal.core.converters.PendingTransactionConverter
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.contacts.model.Contact
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

private val logger = Logger.withTag("TransactionRecordRepository")

class TransactionRecordRepository(
    private val adapterManager: TransactionAdapterManager,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val pendingRepository: PendingTransactionRepository,
    private val pendingConverter: PendingTransactionConverter,
    private val pendingTransactionMatcher: PendingTransactionMatcher,
    private val searchMatcher: TransactionRecordSearchMatcher,
    private val searchVisibility: TransactionSearchVisibility,
    private val locallyCreatedTransactionRepository: LocallyCreatedTransactionRepository,
    private val dispatcherProvider: DispatcherProvider
) : ITransactionRecordRepository {

    @Volatile
    private var selectedFilterTransactionType: FilterTransactionType = FilterTransactionType.All

    private var selectedWallet: TransactionWallet? = null
    private var selectedBlockchain: Blockchain? = null
    private var contact: Contact? = null
    private var selectedSearchQuery: String? = null
    private var selectedPeriod: TimestampRange? = null
    private val searchScanner = TransactionSearchScanner(searchMatcher, searchVisibility)

    // replay = 1: the load can finish before the screen's collector subscribes (both are
    // started by separate coroutines), and a dropped batch leaves the screen without data.
    private val _itemsFlow = MutableSharedFlow<RecordsBatch>(replay = 1, extraBufferCapacity = 4)
    override val itemsFlow: SharedFlow<RecordsBatch> = _itemsFlow.asSharedFlow()

    @Volatile
    private var loadedPageNumber = 0

    // Bumped on every restart from page 1; scan cursors and the auto-scan window live for one session.
    @Volatile
    private var searchSessionId = 0L

    @Volatile
    private var searchProgress = SearchProgress(sessionId = 0L)

    @Volatile
    private var loadedRecordsCount = 0

    private var allNormalLoaded = AtomicBoolean(false)
    private var allExtraLoaded = AtomicBoolean(false)

    // Copy-on-write: on every mutation a freshly built Map is published into this @Volatile ref
    // and the local mutable reference is dropped, so the published instance is never mutated again.
    // Readers take a single volatile read and iterate the snapshot — no CME possible.
    @Volatile
    private var adaptersMap: Map<TransactionWallet, TransactionAdapterWrapper> = emptyMap()

    @Volatile
    private var extraSwapAdaptersMap: Map<TransactionWallet, TransactionAdapterWrapper> = emptyMap()

    private val coroutineScope = CoroutineScope(dispatcherProvider.io)
    private var updatesJob: Job? = null
    private var loadingJob: Job? = null
    private var reloadPending = false

    // Cache of last load request to avoid duplicate work
    @Volatile
    private var lastLoadRequest: Pair<Int, FilterContext>? = null

    @Volatile
    private var walletSetVersion = 0

    private var transactionWallets: List<TransactionWallet> = listOf()
    private var walletsGroupedBySource: List<TransactionWallet> = listOf()

    private val activeAdapters: List<TransactionAdapterWrapper>
        get() {
            val snapshot = adaptersMap
            return activeWallets().mapNotNull { snapshot[it] }
        }

    private val activeSwapExtraAdapters: List<TransactionAdapterWrapper>
        get() {
            val snapshot = extraSwapAdaptersMap
            return activeWallets().mapNotNull { snapshot[it] }
        }

    private fun activeWallets(): List<TransactionWallet> {
        val tmpSelectedWallet = selectedWallet
        val tmpSelectedBlockchain = selectedBlockchain
        return walletsGroupedBySource.filterBySelection(tmpSelectedWallet, tmpSelectedBlockchain)
    }

    /**
     * Captures current filter context for snapshot comparison.
     * Thread-safe: creates new instance with current values.
     */
    private fun getCurrentFilterContext() = FilterContext(
        transactionType = selectedFilterTransactionType,
        wallet = selectedWallet,
        blockchain = selectedBlockchain,
        contact = contact,
        searchQuery = selectedSearchQuery,
        period = selectedPeriod,
        walletSetVersion = walletSetVersion,
        searchSessionId = searchSessionId,
    )

    private fun groupWalletsBySource(transactionWallets: List<TransactionWallet>): List<TransactionWallet> {
        val mergedWallets = mutableListOf<TransactionWallet>()

        transactionWallets.forEach { wallet ->
            when (wallet.source.blockchain.type) {
                BlockchainType.Bitcoin,
                BlockchainType.BitcoinCash,
                BlockchainType.ECash,
                BlockchainType.Litecoin,
                BlockchainType.Dogecoin,
                BlockchainType.PirateCash,
                BlockchainType.Cosanta,
                BlockchainType.Dash,
                BlockchainType.Monero,
                BlockchainType.Beam,
                BlockchainType.Zcash -> mergedWallets.add(wallet)

                BlockchainType.Ethereum,
                BlockchainType.BinanceSmartChain,
                BlockchainType.Polygon,
                BlockchainType.Avalanche,
                BlockchainType.Optimism,
                BlockchainType.Base,
                BlockchainType.ZkSync,
                BlockchainType.RobinhoodChain,
                BlockchainType.ArbitrumOne,
                BlockchainType.Gnosis,
                BlockchainType.Fantom,
                BlockchainType.Solana,
                BlockchainType.Tron,
                BlockchainType.Stellar,
                BlockchainType.Thorchain,
                BlockchainType.Mayachain,
                BlockchainType.Ton -> {
                    if (mergedWallets.none { it.source == wallet.source }) {
                        mergedWallets.add(TransactionWallet(null, wallet.source, null))
                    }
                }

                is BlockchainType.Unsupported -> Unit
            }
        }
        return mergedWallets

    }

    override fun set(
        transactionWallets: List<TransactionWallet>,
        wallet: TransactionWallet?,
        transactionType: FilterTransactionType,
        blockchain: Blockchain?,
        contact: Contact?,
        searchQuery: String?,
        period: TimestampRange?,
    ): Boolean {
        val walletsChanged = updateWalletAdapters(transactionWallets, contact)
        val filtersChanged = updateSelectedFilters(
            wallet = wallet,
            blockchain = blockchain,
            transactionType = transactionType,
            contact = contact,
            searchQuery = searchQuery,
            period = period,
        )

        return walletsChanged || filtersChanged
    }

    override fun reloadItems() = reloadItemsFromStart()

    private fun updateWalletAdapters(
        transactionWallets: List<TransactionWallet>,
        contact: Contact?,
    ): Boolean {
        if (this.transactionWallets == transactionWallets && adaptersMap.isNotEmpty() &&
            !hasNewlyAvailableAdapter()
        ) return false

        this.transactionWallets = transactionWallets
        walletSetVersion++
        walletsGroupedBySource = groupWalletsBySource(transactionWallets)
        adaptersMap = rebuildAdapters(adaptersMap, selectedFilterTransactionType, contact)
        buildExtraSwapAdapters(contact)
        return true
    }

    private fun hasNewlyAvailableAdapter(): Boolean {
        val snapshot = adaptersMap
        return activeTransactionWallets().any { it !in snapshot && adapterManager.getAdapter(it.source) != null }
    }

    private fun updateSelectedFilters(
        wallet: TransactionWallet?,
        blockchain: Blockchain?,
        transactionType: FilterTransactionType,
        contact: Contact?,
        searchQuery: String?,
        period: TimestampRange?,
    ): Boolean {
        var changed = updateSelectedWallet(wallet)
        changed = updateSelectedBlockchain(blockchain) || changed
        changed = updateSelectedTransactionType(transactionType) || changed
        changed = updateSelectedContact(contact) || changed
        changed = updateSelectedSearchQuery(searchQuery) || changed
        changed = updateSelectedPeriod(period) || changed
        return changed
    }

    private fun updateSelectedWallet(wallet: TransactionWallet?): Boolean {
        val changed = selectedWallet != wallet || wallet == null
        if (changed) selectedWallet = wallet
        return changed
    }

    private fun updateSelectedBlockchain(blockchain: Blockchain?): Boolean {
        if (selectedBlockchain == blockchain) return false
        selectedBlockchain = blockchain
        return true
    }

    private fun updateSelectedTransactionType(transactionType: FilterTransactionType): Boolean {
        if (transactionType == selectedFilterTransactionType) return false
        selectedFilterTransactionType = transactionType
        adaptersMap.values.forEach { it.setTransactionType(transactionType) }
        return true
    }

    private fun updateSelectedContact(contact: Contact?): Boolean {
        if (this.contact == contact) return false
        this.contact = contact
        updateContact(contact)
        return true
    }

    private fun updateSelectedSearchQuery(searchQuery: String?): Boolean {
        val normalizedSearchQuery = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        if (selectedSearchQuery == normalizedSearchQuery) return false
        selectedSearchQuery = normalizedSearchQuery
        return true
    }

    private fun updateSelectedPeriod(period: TimestampRange?): Boolean {
        if (selectedPeriod == period) return false
        selectedPeriod = period
        adaptersMap.values.forEach { it.setPeriod(period) }
        extraSwapAdaptersMap.values.forEach { it.setPeriod(period) }
        return true
    }

    private fun rebuildAdapters(
        previousAdapters: Map<TransactionWallet, TransactionAdapterWrapper>,
        transactionType: FilterTransactionType,
        contact: Contact?,
    ): Map<TransactionWallet, TransactionAdapterWrapper> {
        val remainingAdapters = previousAdapters.toMutableMap()
        val newAdapters = mutableMapOf<TransactionWallet, TransactionAdapterWrapper>()
        activeTransactionWallets().forEach { transactionWallet ->
            val adapter = remainingAdapters.remove(transactionWallet)
                ?: createAdapterWrapper(transactionWallet, transactionType, contact)
            adapter?.let { newAdapters[transactionWallet] = it }
        }
        remainingAdapters.values.forEach(TransactionAdapterWrapper::clear)
        return newAdapters
    }

    private fun createAdapterWrapper(
        transactionWallet: TransactionWallet,
        transactionType: FilterTransactionType,
        contact: Contact?,
    ): TransactionAdapterWrapper? {
        return adapterManager.getAdapter(transactionWallet.source)?.let {
            TransactionAdapterWrapper(
                transactionsAdapter = it,
                transactionWallet = transactionWallet,
                transactionType = transactionType,
                contact = contact,
                pendingRepository = pendingRepository,
                pendingConverter = pendingConverter,
                pendingTransactionMatcher = pendingTransactionMatcher,
                locallyCreatedTransactionRepository = locallyCreatedTransactionRepository,
                dispatcherProvider = dispatcherProvider,
                period = selectedPeriod,
            )
        }
    }

    private fun activeTransactionWallets(): List<TransactionWallet> {
        return (transactionWallets + walletsGroupedBySource).distinct()
    }

    private fun buildExtraSwapAdapters(contact: Contact?) {
        extraSwapAdaptersMap = rebuildAdapters(
            previousAdapters = extraSwapAdaptersMap,
            transactionType = FilterTransactionType.All,
            contact = contact,
        )
    }

    private fun updateContact(contact: Contact?) {
        adaptersMap.values.forEach { it.setContact(contact) }
        extraSwapAdaptersMap.values.forEach { it.setContact(contact) }
    }

    private fun reloadItemsFromStart() {
        searchSessionId++
        unsubscribeFromUpdates()
        allNormalLoaded.set(false)
        allExtraLoaded.set(false)
        loadedPageNumber = 1
        loadedRecordsCount = 0
        loadItems(loadedPageNumber)
        subscribeForUpdates()
    }

    override fun loadNext() {
        if (isHistoryExhausted(selectedFilterTransactionType, allNormalLoaded.get(), allExtraLoaded.get())) return
        loadItems(loadedPageNumber + 1)
    }

    // True when a further loadNext would be a no-op.
    private fun isHistoryExhausted(
        transactionType: FilterTransactionType,
        normalLoaded: Boolean,
        extraLoaded: Boolean,
    ): Boolean = normalLoaded && (transactionType != FilterTransactionType.Swap || extraLoaded)

    override suspend fun oldestTimestamp(): Long? = withContext(dispatcherProvider.io) {
        val requestContext = getCurrentFilterContext()
        activeAdapters
            .map { wrapper ->
                async {
                    adapterCallOrNull {
                        wrapper.oldestTimestamp(requestContext.transactionType, requestContext.contact)
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .minOrNull()
    }

    override fun reload() {
        adaptersMap.values.forEach { it.reload() }
        reloadItemsFromStart()
    }

    override fun cancelPendingLoads() {
        loadingJob?.cancel()
        updatesJob?.cancel()
    }

    private fun unsubscribeFromUpdates() {
        updatesJob?.cancel()
    }

    private fun subscribeForUpdates() {
        val updateFlows = activeUpdateFlows()
        if (updateFlows.isEmpty()) return

        updatesJob = coroutineScope.launch {
            updateFlows
                .merge()
                .collect {
                    handleUpdates()
                }
        }
    }

    private fun activeUpdateFlows(): List<Flow<Unit>> = buildList {
        addAll(activeAdapters.map { it.updatedFlow })

        if (selectedFilterTransactionType == FilterTransactionType.Swap) {
            addAll(activeSwapExtraAdapters.map { it.updatedFlow })
        }
        // A provider swap update arrives without an on-chain update, yet it can change which records a search accepts.
        if (selectedFilterTransactionType == FilterTransactionType.Swap || selectedSearchQuery != null) {
            add(swapProviderTransactionsStorage.observeAll().map { Unit })
        }
    }

    @Synchronized
    private fun handleUpdates() {
        allNormalLoaded.set(false)
        allExtraLoaded.set(false)
        if (loadingJob?.isActive == true && lastLoadRequest?.second == getCurrentFilterContext()) {
            // Finish this read and coalesce updates into one authoritative follow-up load.
            reloadPending = true
        } else {
            loadItems(loadedPageNumber)
        }
    }

    @Synchronized
    private fun loadItems(page: Int) {
        // Capture current filter context for validation
        val requestContext = getCurrentFilterContext()
        val currentRequest = Pair(page, requestContext)

        // Optimization: Skip if exact same request is already in progress
        if (loadingJob?.isActive == true && lastLoadRequest == currentRequest) {
            return
        }

        // Cache this request for future comparison
        lastLoadRequest = currentRequest
        reloadPending = false

        // Cancel previous load if it's a different request
        val previousJob = loadingJob
        loadingJob = null
        previousJob?.cancel()

        startLoad(page, requestContext, activeAdapters)
    }

    private fun startLoad(
        page: Int,
        requestContext: FilterContext,
        adapters: List<TransactionAdapterWrapper>,
    ) {
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                loadItemsForContext(page, page * itemsPerPage, requestContext, adapters)
            } catch (e: StaleTransactionReadException) {
                ensureActive()
                queueInvalidatedLoad(requestContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e(e) { "Transaction page load failed" }
            }
        }
        loadingJob = job
        job.invokeOnCompletion { finishLoad(job, page) }
        job.start()
    }

    @Synchronized
    private fun queueInvalidatedLoad(requestContext: FilterContext) {
        // Invalidation can reach the read before its update signal reaches the subscription.
        if (requestContext == getCurrentFilterContext()) reloadPending = true
    }

    @Synchronized
    private fun finishLoad(job: Job, page: Int) {
        if (loadingJob !== job) return
        loadingJob = null
        // Swap widening can publish a deeper page than the one requested.
        if (reloadPending && !job.isCancelled) loadItems(maxOf(page, loadedPageNumber))
    }

    private suspend fun CoroutineScope.loadItemsForContext(
        page: Int,
        itemsCount: Int,
        requestContext: FilterContext,
        adapters: List<TransactionAdapterWrapper>,
    ) {
        val searchQuery = requestContext.searchQuery
        if (searchQuery == null) {
            val revisions = (adapters + activeSearchExtraAdapters(requestContext))
                .associateWith { it.cacheRevision }
            loadRegularPage(page, itemsCount, requestContext, adapters, revisions)
        } else {
            // A search cursor applies wallet updates itself, so an update does not discard the page.
            loadSearchPage(page, itemsCount, requestContext, searchQuery, adapters)
        }
    }

    private suspend fun CoroutineScope.loadRegularPage(
        page: Int,
        itemsCount: Int,
        requestContext: FilterContext,
        adapters: List<TransactionAdapterWrapper>,
        revisions: Map<TransactionAdapterWrapper, Long>,
    ) {
        val normalRecords = loadAdapterRecords(
            adapters = adapters,
            limit = itemsCount,
            transactionType = requestContext.transactionType,
            requestContext = requestContext,
        )
        if (!isActive || requestContext.isStale(revisions)) return

        val extraRecords = loadRegularSwapExtraRecords(itemsCount, requestContext)
        if (!isActive || requestContext.isStale(revisions)) return

        // Only a rendered row requests the next page, so a window that adds no new row must widen here.
        if (shouldWidenSwapWindow(page, itemsCount, requestContext, normalRecords, extraRecords)) {
            return loadRegularPage(page * 2, page * 2 * itemsPerPage, requestContext, adapters, revisions)
        }

        if (extraRecords.complete && extraRecords.sourceRecordsCount < itemsCount) {
            allExtraLoaded.set(true)
        }
        // Selected wallets whose adapter could not be resolved read nothing at all, so the empty
        // result is vacuous and must not be published as a trustworthy empty history.
        val nothingWasRead = adapters.isEmpty() && activeWallets().isNotEmpty()
        handleRecords(
            records = normalRecords.records,
            extraRecords = extraRecords.records,
            page = page,
            complete = normalRecords.complete && !nothingWasRead,
            extraComplete = extraRecords.complete,
            transactionType = requestContext.transactionType,
        )
    }

    private fun shouldWidenSwapWindow(
        page: Int,
        itemsCount: Int,
        requestContext: FilterContext,
        normalRecords: AdapterRecords,
        extraRecords: ExtraRecordsResult,
    ): Boolean = requestContext.transactionType == FilterTransactionType.Swap &&
        normalRecords.complete && extraRecords.complete &&
        distinctRecordsCount(normalRecords.records, extraRecords.records) <= alreadyShownRecordsCount(page) &&
        extraRecords.sourceRecordsCount >= itemsCount &&
        page * 2 * itemsPerPage <= TransactionSearchScanner.SCAN_BUDGET_PER_PAGE

    // A re-read of the current page must not compare: an unchanged list would widen on every update.
    private fun alreadyShownRecordsCount(page: Int): Int =
        if (page > loadedPageNumber) loadedRecordsCount else 0

    // The sources can return the same record and the screen deduplicates by uid.
    private fun distinctRecordsCount(
        normalRecords: List<TransactionRecord>,
        extraRecords: List<TransactionRecord>,
    ): Int = (normalRecords + extraRecords).distinctBy { it.uid }.size

    private suspend fun CoroutineScope.loadSearchPage(
        page: Int,
        itemsCount: Int,
        requestContext: FilterContext,
        query: String,
        adapters: List<TransactionAdapterWrapper>,
    ) {
        val progress = searchProgress.takeIf { it.sessionId == requestContext.searchSessionId }
            ?: SearchProgress(requestContext.searchSessionId)
        val refresh = progress.isRefresh(page)
        val result = searchScanner.loadSearchItems(
            expectedItemsCount = itemsCount,
            requestContext = requestContext,
            query = query,
            normalAdapters = adapters,
            extraAdapters = activeSearchExtraAdapters(requestContext),
            reachedDepths = progress.depths,
            refresh = refresh,
        )
        if (!isActive || requestContext != getCurrentFilterContext()) return
        searchProgress = progress.completed(page, result.depths)

        allNormalLoaded.set(result.normalLoaded)
        allExtraLoaded.set(result.extraLoaded)

        val searchExhausted =
            isHistoryExhausted(requestContext.transactionType, result.normalLoaded, result.extraLoaded)
        emitSearchRecords(result, page, searchExhausted, requestContext.searchSessionId, refresh)
    }

    private suspend fun loadRegularSwapExtraRecords(
        itemsCount: Int,
        requestContext: FilterContext,
    ): ExtraRecordsResult {
        if (requestContext.transactionType != FilterTransactionType.Swap) {
            return ExtraRecordsResult(emptyList(), 0, complete = true)
        }

        val loaded = loadAdapterRecords(
            adapters = activeSwapExtraAdapters,
            limit = itemsCount,
            transactionType = FilterTransactionType.All,
            requestContext = requestContext,
        )
        return ExtraRecordsResult(
            records = loaded.records.filter(searchVisibility::isExtraSwapRecord),
            sourceRecordsCount = loaded.records.size,
            complete = loaded.complete,
        )
    }

    private suspend fun loadAdapterRecords(
        adapters: List<TransactionAdapterWrapper>,
        limit: Int,
        transactionType: FilterTransactionType,
        requestContext: FilterContext,
    ): AdapterRecords = coroutineScope {
        val pages = adapters
            .map { wrapper ->
                async {
                    adapterCallOrNull {
                        wrapper.get(
                            limit = limit,
                            requestedFilterType = transactionType,
                            requestedContact = requestContext.contact,
                            requestedPeriod = requestContext.period,
                        )
                    }
                }
            }
            .awaitAll()

        AdapterRecords(
            records = pages.filterNotNull().flatten(),
            complete = pages.none { it == null },
        )
    }

    private fun activeSearchExtraAdapters(requestContext: FilterContext): List<TransactionAdapterWrapper> {
        return if (requestContext.transactionType == FilterTransactionType.Swap) {
            activeSwapExtraAdapters
        } else {
            emptyList()
        }
    }

    private fun FilterContext.isStale(revisions: Map<TransactionAdapterWrapper, Long>): Boolean {
        if (this != getCurrentFilterContext()) return true
        if (revisions.any { (adapter, revision) -> adapter.cacheRevision != revision }) {
            throw StaleTransactionReadException()
        }
        return false
    }

    private fun emitSearchRecords(
        result: SearchLoadResult,
        page: Int,
        searchExhausted: Boolean,
        searchSessionId: Long,
        refresh: Boolean,
    ) {
        loadedPageNumber = page
        _itemsFlow.tryEmit(
            RecordsBatch(
                records = result.records.sortedDescending().take(page * itemsPerPage),
                searchCompleted = true,
                searchExhausted = searchExhausted,
                searchScannedCount = result.scannedCount,
                searchSessionId = searchSessionId,
                searchTimedOut = result.timedOut,
                searchRefresh = refresh,
            )
        )
    }

    override fun clear() {
        val previousAdapters = adaptersMap
        adaptersMap = emptyMap()
        previousAdapters.values.forEach(TransactionAdapterWrapper::clear)

        val previousExtraSwapAdapters = extraSwapAdaptersMap
        extraSwapAdaptersMap = emptyMap()
        previousExtraSwapAdapters.values.forEach(TransactionAdapterWrapper::clear)

        coroutineScope.cancel()
    }

    private fun handleRecords(
        records: List<TransactionRecord>,
        extraRecords: List<TransactionRecord>,
        page: Int,
        complete: Boolean,
        extraComplete: Boolean,
        transactionType: FilterTransactionType,
    ) {
        val expectedItemsCount = page * itemsPerPage

        val normalSortedRecords = records
            .sortedDescending()
            .take(expectedItemsCount)

        // A source that timed out contributes nothing, so a short page proves the history is
        // exhausted only when every source actually answered - otherwise loadNext() would be
        // refused for good and the rest of the history would stay unreachable.
        if (complete && normalSortedRecords.size < expectedItemsCount) {
            allNormalLoaded.set(true)
        }

        val extraSortedRecords = extraRecords
            .sortedDescending()
            .take(expectedItemsCount)

        loadedPageNumber = page
        loadedRecordsCount = distinctRecordsCount(records, extraRecords)
        val sortedRecords = (normalSortedRecords + extraSortedRecords).sortedDescending()
        _itemsFlow.tryEmit(
            RecordsBatch(
                records = sortedRecords,
                loadFailed = sortedRecords.isEmpty() && !(complete && extraComplete),
                historyExhausted = complete &&
                    isHistoryExhausted(transactionType, allNormalLoaded.get(), allExtraLoaded.get()),
            )
        )
    }

    companion object {
        const val itemsPerPage = 20
    }

}

private const val ADAPTER_CALL_TIMEOUT_MS = 3000L

/**
 * A stalled or failing source must contribute nothing rather than strand the whole load: without
 * this the page never reaches its emission and the screen keeps waiting for records forever.
 */
private suspend fun <T> adapterCallOrNull(block: suspend () -> T): T? =
    try {
        withTimeoutOrNull(ADAPTER_CALL_TIMEOUT_MS) { block() }
            .also { if (it == null) logger.w { "Transaction adapter call timed out" } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.e(e) { "Transaction adapter call failed" }
        null
    }

/**
 * Scans adapter pages newest-first across all active sources to satisfy an in-app search query,
 * sharing a bounded scan budget so deeply buried matches are not starved by long-history sources.
 */
private class TransactionSearchScanner(
    private val searchMatcher: TransactionRecordSearchMatcher,
    private val searchVisibility: TransactionSearchVisibility,
) {

    suspend fun loadSearchItems(
        expectedItemsCount: Int,
        requestContext: FilterContext,
        query: String,
        normalAdapters: List<TransactionAdapterWrapper>,
        extraAdapters: List<TransactionAdapterWrapper>,
        reachedDepths: Map<TransactionAdapterWrapper, SourceDepth>,
        refresh: Boolean,
    ): SearchLoadResult {
        val tab = requestContext.transactionType
        val key = SourceSearch.Key(
            query = searchMatcher.prepare(query, searchVisibility.readsProviderSwaps(tab)),
            tab = tab,
            extraSource = false,
            hideSuspiciousTx = searchVisibility.hideSuspiciousTx,
            sessionId = requestContext.searchSessionId,
            period = requestContext.period,
        )
        val normalSources = normalAdapters.searchSources(tab, key)
        val extraSources = extraAdapters.searchSources(FilterTransactionType.All, key.copy(extraSource = true))
        val allSources = normalSources + extraSources
        allSources.forEach { restoreSearchSource(it, reachedDepths[it.wrapper], expectedItemsCount, requestContext) }
        // A refresh only lets sources whose reached end may have grown read further.
        val advancingSources = if (refresh) {
            allSources.filter { reachedDepths[it.wrapper]?.exhausted != false }
        } else {
            allSources
        }
        val timedOut = coroutineScope {
            // A slow device must not keep the user waiting for the whole record budget.
            val deadline = launch { delay(SCAN_TIME_LIMIT_PER_PAGE) }
            scanSearchSources(
                sources = advancingSources,
                normalSources = normalSources,
                extraSources = extraSources,
                expectedItemsCount = expectedItemsCount,
                requestContext = requestContext,
                scanBudget = SCAN_BUDGET_PER_PAGE,
                deadline = deadline,
            )
            deadline.isCompleted.also { deadline.cancel() }
        }
        return searchLoadResult(normalSources, extraSources, expectedItemsCount, timedOut)
    }

    private fun searchLoadResult(
        normalSources: List<SearchSourceState>,
        extraSources: List<SearchSourceState>,
        expectedItemsCount: Int,
        timedOut: Boolean,
    ): SearchLoadResult {
        val allSources = normalSources + extraSources
        // A full page may be cut by the wrapper's limit, so it does not prove every match was shown.
        val truncated = allSources.sumOf { it.records.size } >= expectedItemsCount
        return SearchLoadResult(
            records = mergedSearchRecords(normalSources, extraSources, expectedItemsCount),
            normalLoaded = !truncated && searchSourcesLoaded(normalSources),
            extraLoaded = !truncated && searchSourcesLoaded(extraSources),
            scannedCount = allSources.sumOf { it.scannedCount },
            depths = allSources.filterNot { it.failed }
                .associate { it.wrapper to SourceDepth(it.scannedCount, it.exhausted) },
            timedOut = timedOut || allSources.any { it.failed },
        )
    }

    // Depth reached by earlier steps of the session is re-read here without charging the scan budget.
    private suspend fun restoreSearchSource(
        source: SearchSourceState,
        reached: SourceDepth?,
        expectedItemsCount: Int,
        requestContext: FilterContext,
    ) {
        val targetDepth = reached?.scannedCount ?: return
        while (!source.finished && source.scannedCount < targetDepth) {
            val previousScannedCount = source.scannedCount
            val scanLimit = minOf(targetDepth, previousScannedCount + searchBatchSize)
            readSearchSource(source, scanLimit, expectedItemsCount, requestContext)
            if (source.scannedCount == previousScannedCount) return
        }
    }

    private fun List<TransactionAdapterWrapper>.searchSources(
        transactionType: FilterTransactionType,
        key: SourceSearch.Key,
    ): List<SearchSourceState> {
        val search = SourceSearch(key, searchMatcher, searchVisibility)
        return map { SearchSourceState(it, transactionType, search) }
    }

    private suspend fun scanSearchSources(
        sources: List<SearchSourceState>,
        normalSources: List<SearchSourceState>,
        extraSources: List<SearchSourceState>,
        expectedItemsCount: Int,
        requestContext: FilterContext,
        scanBudget: Int,
        deadline: Job,
    ) {
        var totalScannedCount = 0
        // Cutoff from the previous round; null until the page is full. The cutoff only grows as
        // newer matches are found, so reusing the previous value to skip settled sources is
        // conservative — a source settled against an older cutoff is settled against the newer one.
        var cutoffTimestamp: Long? = null

        while (
            totalScannedCount < scanBudget &&
            sources.any { !it.finished }
        ) {
            val round = scanSearchRound(
                sources = sources,
                totalScannedCount = totalScannedCount,
                cutoffTimestamp = cutoffTimestamp,
                expectedItemsCount = expectedItemsCount,
                requestContext = requestContext,
                scanBudget = scanBudget,
            )
            totalScannedCount = round.totalScannedCount

            cutoffTimestamp = searchCutoff(normalSources, extraSources, expectedItemsCount)
            // Checked between rounds, so a hanging source cannot starve the others of their read.
            if (!round.progressed || deadline.isCompleted || searchPageSettled(sources, cutoffTimestamp)) {
                break
            }
        }
    }

    /**
     * Timestamp of the last record on the current top page, or null while the page is not yet full.
     * A source scanned newest-first can no longer affect the page once its frontier (oldest scanned
     * record) drops below this cutoff.
     */
    private fun searchCutoff(
        normalSources: List<SearchSourceState>,
        extraSources: List<SearchSourceState>,
        expectedItemsCount: Int,
    ): Long? {
        val mergedRecords = mergedSearchRecords(normalSources, extraSources, expectedItemsCount)
        return if (mergedRecords.size < expectedItemsCount) null else mergedRecords.last().timestamp
    }

    /**
     * A settled source cannot surface a record newer than the current page cutoff: it is scanned
     * newest-first, so once its frontier is older than the cutoff every deeper record is older too.
     * Settled sources are skipped in later rounds so they stop draining the shared scan budget and
     * starving the one source that may still hold a newer match behind a long run of non-matches.
     */
    private fun SearchSourceState.isSettled(cutoffTimestamp: Long?): Boolean {
        if (finished) return true
        if (cutoffTimestamp == null) return false
        return (frontierTimestamp ?: Long.MAX_VALUE) < cutoffTimestamp
    }

    private fun searchPageSettled(
        sources: List<SearchSourceState>,
        cutoffTimestamp: Long?,
    ): Boolean {
        if (cutoffTimestamp == null) {
            return false
        }
        return sources.all { it.isSettled(cutoffTimestamp) }
    }

    private suspend fun scanSearchRound(
        sources: List<SearchSourceState>,
        totalScannedCount: Int,
        cutoffTimestamp: Long?,
        expectedItemsCount: Int,
        requestContext: FilterContext,
        scanBudget: Int,
    ): SearchRoundResult {
        var updatedTotalScannedCount = totalScannedCount
        var progressed = false

        sources.forEach { source ->
            val previousScannedCount = source.scannedCount
            scanSearchSource(
                source = source,
                totalScannedCount = updatedTotalScannedCount,
                cutoffTimestamp = cutoffTimestamp,
                expectedItemsCount = expectedItemsCount,
                requestContext = requestContext,
                scanBudget = scanBudget,
            )
            updatedTotalScannedCount += (source.scannedCount - previousScannedCount).coerceAtLeast(0)
            progressed = progressed || source.scannedCount > previousScannedCount
        }

        return SearchRoundResult(updatedTotalScannedCount, progressed)
    }

    private suspend fun scanSearchSource(
        source: SearchSourceState,
        totalScannedCount: Int,
        cutoffTimestamp: Long?,
        expectedItemsCount: Int,
        requestContext: FilterContext,
        scanBudget: Int,
    ) {
        // A spent budget is not an exhausted source: the next page continues from here.
        if (source.isSettled(cutoffTimestamp) || totalScannedCount >= scanBudget) {
            return
        }

        val scanLimit = source.scannedCount + minOf(searchBatchSize, scanBudget - totalScannedCount)
        readSearchSource(source, scanLimit, expectedItemsCount, requestContext)
    }

    private suspend fun readSearchSource(
        source: SearchSourceState,
        scanLimit: Int,
        expectedItemsCount: Int,
        requestContext: FilterContext,
    ) {
        val page = adapterCallOrNull {
            source.wrapper.search(
                limit = expectedItemsCount,
                scanLimit = scanLimit,
                requestedFilterType = source.transactionType,
                requestedContact = requestContext.contact,
                sourceSearch = source.search,
            )
        }

        if (page == null) {
            source.failed = true
            return
        }

        source.scannedCount = page.scannedCount
        source.records = page.records
        source.frontierTimestamp = page.frontierTimestamp
        source.exhausted = page.exhausted
    }

    // A source that failed to answer was not searched, so it must not end the search.
    private fun searchSourcesLoaded(sources: List<SearchSourceState>): Boolean = sources.all { it.exhausted }

    private fun mergedSearchRecords(
        normalSources: List<SearchSourceState>,
        extraSources: List<SearchSourceState>,
        expectedItemsCount: Int,
    ): List<TransactionRecord> {
        return (normalSources.flatMap { it.records } + extraSources.flatMap { it.records })
            .sortedDescending()
            .take(expectedItemsCount)
    }

    companion object {
        private const val searchBatchSize = 100
        const val SCAN_BUDGET_PER_PAGE = 5000
        private const val SCAN_TIME_LIMIT_PER_PAGE = 3_000L
    }
}

/**
 * Snapshot of filter context at the time of load request.
 * Used to detect filter changes during async loading and discard stale results.
 */
private data class FilterContext(
    val transactionType: FilterTransactionType,
    val wallet: TransactionWallet?,
    val blockchain: Blockchain?,
    val contact: Contact?,
    val searchQuery: String?,
    val period: TimestampRange?,
    val walletSetVersion: Int,
    val searchSessionId: Long,
)

private data class SearchLoadResult(
    val records: List<TransactionRecord>,
    val normalLoaded: Boolean,
    val extraLoaded: Boolean,
    val scannedCount: Int,
    val depths: Map<TransactionAdapterWrapper, SourceDepth>,
    val timedOut: Boolean,
)

private data class SourceDepth(
    val scannedCount: Int,
    val exhausted: Boolean,
)

// Depth every search source reached in one session; later loads restore it for free.
private data class SearchProgress(
    val sessionId: Long,
    val completedPage: Int = 0,
    val depths: Map<TransactionAdapterWrapper, SourceDepth> = emptyMap(),
) {
    fun isRefresh(page: Int): Boolean = page <= completedPage

    // A restore cut short by a full page keeps the deeper depth reached before.
    fun completed(page: Int, reached: Map<TransactionAdapterWrapper, SourceDepth>) = copy(
        completedPage = maxOf(page, completedPage),
        depths = depths + reached.filter { (wrapper, depth) ->
            depth.scannedCount >= (depths[wrapper]?.scannedCount ?: 0)
        },
    )
}

private data class AdapterRecords(
    val records: List<TransactionRecord>,
    val complete: Boolean,
)

private data class ExtraRecordsResult(
    val records: List<TransactionRecord>,
    val sourceRecordsCount: Int,
    val complete: Boolean,
)

private data class SearchRoundResult(
    val totalScannedCount: Int,
    val progressed: Boolean,
)

private class SearchSourceState(
    val wrapper: TransactionAdapterWrapper,
    val transactionType: FilterTransactionType,
    val search: SourceSearch,
) {
    var records: List<TransactionRecord> = emptyList()
    var scannedCount: Int = 0
    var exhausted: Boolean = false
    var failed: Boolean = false
    val finished: Boolean get() = exhausted || failed
    // Timestamp of the oldest record scanned so far. Since a source is scanned newest-first,
    // deeper records are all older than this, so once it drops below the page cutoff the
    // source can no longer contribute to the top results.
    var frontierTimestamp: Long? = null
}
