package cash.p.terminal.modules.transactions

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.converters.PendingTransactionConverter
import cash.p.terminal.core.managers.CoinManager
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.managers.PendingTransactionMatchKind
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.PendingTransactionEntity
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.inject
import kotlin.math.abs
import kotlin.coroutines.cancellation.CancellationException

class TransactionAdapterWrapper(
    private val transactionsAdapter: ITransactionsAdapter,
    val transactionWallet: TransactionWallet,
    @Volatile
    private var transactionType: FilterTransactionType,
    @Volatile
    private var contact: Contact?,
    private val pendingRepository: PendingTransactionRepository,
    private val pendingConverter: PendingTransactionConverter,
    private val pendingTransactionMatcher: PendingTransactionMatcher,
    private val locallyCreatedTransactionRepository: LocallyCreatedTransactionRepository,
    dispatcherProvider: DispatcherProvider,
    @Volatile
    private var period: TimestampRange? = null,
) : Clearable {
    private companion object {
        const val CREATED_MARK_MIN_CONFIDENCE = 0.9
        const val SEARCH_HEAD_SIZE = 100
        const val MAX_SEARCH_SYNC_PASSES = 3
    }

    private data class PendingRealMatchCandidate(
        val realIndex: Int,
        val confidence: Double,
        val timestampDifference: Long,
        val kind: PendingTransactionMatchKind,
    )

    private data class PendingRealMatch(
        val pendingIndex: Int,
        val confidence: Double,
        val kind: PendingTransactionMatchKind,
    )

    // Use MutableSharedFlow for updates
    private val _updatedFlow = MutableSharedFlow<Unit>(replay = 0)
    val updatedFlow: SharedFlow<Unit> get() = _updatedFlow.asSharedFlow()

    private data class RecordsCache(
        val revision: Long = 0,
        val records: List<TransactionRecord> = emptyList(),
        val allLoaded: Boolean = false,
    )

    // revision is the cache revision the newest records were last read at.
    private class SearchCursor(val key: SourceSearch.Key, var revision: Long) {
        var newestRawRecord: TransactionRecord? = null
        var lastRawRecord: TransactionRecord? = null
        var scannedCount = 0
        val matches = mutableListOf<TransactionRecord>()
        var allLoaded = false
    }

    @Volatile
    private var cache = RecordsCache()

    // Guarded by getMutex.
    private var searchCursor: SearchCursor? = null

    internal val cacheRevision: Long get() = cache.revision
    private val coinManager: CoinManager by inject(CoinManager::class.java)

    private val coroutineScope = CoroutineScope(dispatcherProvider.io + SupervisorJob())
    private val getMutex = Mutex()
    private var updatesJob: Job? = null

    val address: String?
        get() = addressOf(contact)

    private fun addressOf(contact: Contact?): String? = contact
        ?.addresses
        ?.find { it.blockchain == transactionWallet.source.blockchain }
        ?.address

    init {
        subscribeForUpdates()
    }

    data class SearchPage(
        val records: List<TransactionRecord>,
        val exhausted: Boolean,
        val scannedCount: Int,
        // Timestamp of the oldest scanned record; lets the caller decide whether deeper
        // scanning of this source could still surface a newer match. Null when nothing scanned.
        val frontierTimestamp: Long?,
    )

    private val emptySearchPage = SearchPage(
        records = emptyList(),
        exhausted = true,
        scannedCount = 0,
        frontierTimestamp = null,
    )

    fun reload() {
        resetCacheAndResubscribe()
    }

    fun setTransactionType(transactionType: FilterTransactionType) {
        this.transactionType = transactionType
        resetCacheAndResubscribe()
    }

    fun setContact(contact: Contact?) {
        this.contact = contact
        resetCacheAndResubscribe()
    }

    fun setPeriod(period: TimestampRange?) {
        this.period = period
        resetCacheAndResubscribe()
    }

    private fun resetCacheAndResubscribe() {
        invalidateCache()
        subscribeForUpdates()
    }

    private fun subscribeForUpdates() {
        // Snapshot: capture current filter parameters at subscription time
        val expectedType = transactionType
        val expectedContact = contact
        val expectedAddress = address

        updatesJob?.cancel()

        if (expectedContact != null && expectedAddress == null) return

        updatesJob = coroutineScope.launch {
            val walletId = transactionWallet.source.account.id

            merge(
                transactionsAdapter
                    .getTransactionRecordsFlow(
                        transactionWallet.token,
                        expectedType,     // Use snapshot!
                        expectedAddress   // Use snapshot!
                    ),
                pendingRepository.getActivePendingFlow(walletId),
                // Not content-filtered, unlike getTransactionRecordsFlow (which drops empty
                // emissions for Incoming/Outgoing) - ensures a deletion reloads those tabs too.
                transactionsAdapter.getTransactionsReloadSignalFlow(),
            ).collectLatest {
                if (!isActive ||
                    transactionType != expectedType ||
                    contact != expectedContact
                ) {
                    return@collectLatest
                }

                invalidateCache()
                _updatedFlow.emit(Unit)

            }
        }
    }

    suspend fun get(
        limit: Int,
        requestedFilterType: FilterTransactionType,
        requestedContact: Contact?,
        requestedPeriod: TimestampRange? = null,
    ): List<TransactionRecord> = getMutex.withLock {
        // Check if cache is valid for the requested filter
        if (!matchesFilter(requestedFilterType, requestedContact, requestedPeriod)) {
            // Cache is stale for the requested filter - return empty list
            return@withLock emptyList()
        }

        val requestedAddress = addressOf(requestedContact)

        val currentCache = cache
        if (requestedContact != null && requestedAddress == null) return@withLock emptyList()
        val loadedCache = if (currentCache.records.size >= limit || currentCache.allLoaded) {
            currentCache
        } else {
            loadRecords(currentCache, limit, requestedFilterType, requestedContact, requestedAddress, requestedPeriod)
        }
        loadedCache.records.take(limit)
    }

    suspend fun oldestTimestamp(requestedFilterType: FilterTransactionType, requestedContact: Contact?): Long? {
        val requestedAddress = addressOf(requestedContact)
        if (requestedContact != null && requestedAddress == null) return null
        return transactionsAdapter.getOldestTransactionTimestamp(
            transactionWallet.token,
            requestedFilterType,
            requestedAddress,
        )
    }

    suspend fun search(
        limit: Int,
        scanLimit: Int,
        requestedFilterType: FilterTransactionType,
        requestedContact: Contact?,
        sourceSearch: SourceSearch,
    ): SearchPage = getMutex.withLock {
        if (!matchesFilter(requestedFilterType, requestedContact, sourceSearch.key.period)) {
            return@withLock emptySearchPage
        }

        val requestedAddress = addressOf(requestedContact)

        if (requestedContact != null && requestedAddress == null) {
            return@withLock emptySearchPage
        }

        var cursor = searchCursor?.takeIf { it.key == sourceSearch.key }
            ?: SearchCursor(sourceSearch.key, cache.revision).also { searchCursor = it }
        // An update during the read is applied before returning. A busy address gets a bounded number
        // of passes; whatever is left is applied by the refresh the update triggers.
        var passes = 0
        do {
            cursor = syncedSearchCursor(cursor, sourceSearch, requestedFilterType, requestedContact, requestedAddress)
            while (cursor.matches.size < limit && cursor.scannedCount < scanLimit && !cursor.allLoaded) {
                val count = scanLimit - cursor.scannedCount
                readSearchChunk(cursor, count, sourceSearch, requestedFilterType, requestedContact, requestedAddress)
            }
        } while (cursor.revision != cache.revision && ++passes < MAX_SEARCH_SYNC_PASSES)

        SearchPage(
            records = cursor.matches.take(limit),
            exhausted = cursor.allLoaded,
            scannedCount = cursor.scannedCount,
            frontierTimestamp = cursor.lastRawRecord?.timestamp,
        )
    }

    /**
     * New records arrive on top and recent ones may change, so an update re-reads only the newest
     * records and keeps the scanned depth; a full rescan would never finish on a busy address.
     */
    private suspend fun syncedSearchCursor(
        cursor: SearchCursor,
        sourceSearch: SourceSearch,
        requestedFilterType: FilterTransactionType,
        requestedContact: Contact?,
        requestedAddress: String?,
    ): SearchCursor {
        val revision = cache.revision
        if (cursor.revision == revision) return cursor
        val knownNewest = cursor.newestRawRecord
        val head = if (knownNewest != null && cursor.scannedCount >= SEARCH_HEAD_SIZE) {
            readSearchHead(knownNewest, requestedFilterType, requestedAddress, sourceSearch.key.period)
        } else {
            emptyList()
        }
        checkFilter(requestedFilterType, requestedContact, sourceSearch.key.period)
        if (knownNewest == null || head.isEmpty()) {
            return SearchCursor(cursor.key, revision).also { searchCursor = it }
        }

        val headMatches = mergePendingAndReal(head).filter(sourceSearch::accepts)
        currentCoroutineContext().ensureActive()
        checkFilter(requestedFilterType, requestedContact, sourceSearch.key.period)
        cursor.applyHead(head, headMatches, knownNewest)
        cursor.revision = revision
        return cursor
    }

    // Older matches newer than the re-read head were removed from history; pending ones are re-merged.
    private fun SearchCursor.applyHead(
        head: List<TransactionRecord>,
        headMatches: List<TransactionRecord>,
        knownNewest: TransactionRecord,
    ) {
        val headUids = head.mapTo(HashSet()) { it.uid }
        val oldestHeadTimestamp = head.last().timestamp
        val olderMatches = matches.filter {
            it !is PendingTransactionRecord && it.uid !in headUids && it.timestamp <= oldestHeadTimestamp
        }
        matches.clear()
        matches += headMatches + olderMatches
        scannedCount += head.count { it > knownNewest }
        newestRawRecord = head.first()
    }

    // Reads from the top until it reaches the newest record scanned before, at least one chunk deep.
    private suspend fun readSearchHead(
        knownNewest: TransactionRecord,
        requestedFilterType: FilterTransactionType,
        requestedAddress: String?,
        requestedPeriod: TimestampRange?,
    ): List<TransactionRecord> {
        val head = mutableListOf<TransactionRecord>()
        do {
            val chunk = transactionsAdapter.getTransactions(
                from = head.lastOrNull(),
                token = transactionWallet.token,
                limit = SEARCH_HEAD_SIZE,
                transactionType = requestedFilterType,
                address = requestedAddress,
                period = requestedPeriod,
            )
            currentCoroutineContext().ensureActive()
            head += chunk
        } while (chunk.size == SEARCH_HEAD_SIZE && chunk.none { it <= knownNewest })
        return head
    }

    // Each record is accepted or dropped once; only accepted ones are kept, the display cache is never written.
    private suspend fun readSearchChunk(
        cursor: SearchCursor,
        count: Int,
        sourceSearch: SourceSearch,
        requestedFilterType: FilterTransactionType,
        requestedContact: Contact?,
        requestedAddress: String?,
    ) {
        val requestedPeriod = sourceSearch.key.period
        val received = transactionsAdapter.getTransactions(
            from = cursor.lastRawRecord,
            token = transactionWallet.token,
            limit = count,
            transactionType = requestedFilterType,
            address = requestedAddress,
            period = requestedPeriod,
        )
        currentCoroutineContext().ensureActive()
        checkFilter(requestedFilterType, requestedContact, requestedPeriod)
        // Pending records are the newest, so only the first chunk merges them.
        val records = if (cursor.scannedCount == 0) mergePendingAndReal(received) else received
        currentCoroutineContext().ensureActive()
        checkFilter(requestedFilterType, requestedContact, requestedPeriod)

        if (cursor.scannedCount == 0) cursor.newestRawRecord = received.firstOrNull()
        cursor.matches += records.filter(sourceSearch::accepts)
        cursor.lastRawRecord = received.lastOrNull() ?: cursor.lastRawRecord
        cursor.scannedCount += received.size
        cursor.allLoaded = received.size < count
    }

    @Synchronized
    private fun invalidateCache() {
        cache = RecordsCache(revision = cache.revision + 1)
    }

    private suspend fun loadRecords(
        currentCache: RecordsCache,
        limit: Int,
        requestedFilterType: FilterTransactionType,
        requestedContact: Contact?,
        requestedAddress: String?,
        requestedPeriod: TimestampRange?,
    ): RecordsCache {
        val count = limit - currentCache.records.size
        val received = transactionsAdapter.getTransactions(
            from = currentCache.records.lastOrNull(),
            token = transactionWallet.token,
            limit = count,
            transactionType = requestedFilterType,
            address = requestedAddress,
            period = requestedPeriod,
        )
        currentCoroutineContext().ensureActive()
        checkCacheRevision(currentCache.revision, requestedFilterType, requestedContact, requestedPeriod)
        val records = mergePendingAndReal(currentCache.records + received)
        currentCoroutineContext().ensureActive()
        return synchronized(this) {
            checkCacheRevision(currentCache.revision, requestedFilterType, requestedContact, requestedPeriod)
            currentCache.copy(records = records, allLoaded = received.size < count).also { cache = it }
        }
    }

    private fun checkCacheRevision(
        revision: Long,
        requestedFilterType: FilterTransactionType,
        requestedContact: Contact?,
        requestedPeriod: TimestampRange?,
    ) {
        if (cache.revision != revision) throw StaleTransactionReadException()
        checkFilter(requestedFilterType, requestedContact, requestedPeriod)
    }

    private fun checkFilter(
        requestedFilterType: FilterTransactionType,
        requestedContact: Contact?,
        requestedPeriod: TimestampRange?,
    ) {
        if (!matchesFilter(requestedFilterType, requestedContact, requestedPeriod)) {
            throw StaleTransactionReadException()
        }
    }

    private fun matchesFilter(
        requestedFilterType: FilterTransactionType,
        requestedContact: Contact?,
        requestedPeriod: TimestampRange?,
    ): Boolean = transactionType == requestedFilterType && contact == requestedContact && period == requestedPeriod

    private fun getPending(pendingEntities: List<PendingTransactionEntity>): List<TransactionRecord> {
        val blockchainUid = transactionWallet.source.blockchain.type.uid
        val filtered = pendingEntities.filter { it.blockchainTypeUid == blockchainUid }

        return if (transactionWallet.token != null) {
            val token = transactionWallet.token
            filtered
                .filter { it.tokenTypeId == token.type.id }
                .map { pendingConverter.convert(it, token) }
        } else {
            filtered
                .mapNotNull {
                    val tokenType = TokenType.fromId(it.tokenTypeId)
                    val token = tryOrNull {
                        coinManager.getToken(
                            TokenQuery(
                                BlockchainType.fromUid(it.blockchainTypeUid),
                                tokenType
                            )
                        )
                    } ?: return@mapNotNull null
                    pendingConverter.convert(it, token)
                }
        }
    }

    // Pending records are created now, so a past period drops them.
    private suspend fun mergePendingAndReal(realRecords: List<TransactionRecord>): List<TransactionRecord> =
        mergeAllPendingAndReal(realRecords).filterInPeriod(period) { it.timestamp }

    private suspend fun mergeAllPendingAndReal(realRecords: List<TransactionRecord>): List<TransactionRecord> {
        if (
            transactionType != FilterTransactionType.All &&
            transactionType != FilterTransactionType.Outgoing
        ) {
            // Pending transactions are always for outgoing transactions
            return realRecords
        }

        val walletId = transactionWallet.source.account.id

        return try {
            val pendingEntities = pendingRepository.getPendingForWallet(walletId)
            val pendingRecords = getPending(pendingEntities)
            val matchedPendingByReal = matchedPendingByRealIndexes(pendingRecords, realRecords)
            val adjustedRealRecords = adjustMatchedRealRecords(realRecords, pendingRecords, matchedPendingByReal)
            markMatchedRealRecordsCreated(matchedPendingByReal, realRecords, pendingRecords)
            val matchedPendingIndexes = matchedPendingByReal.values.mapTo(HashSet()) { it.pendingIndex }
            val filteredPending = filterDuplicatedPending(
                pendingRecords = pendingRecords,
                realRecords = adjustedRealRecords,
                matchedPendingIndexes = matchedPendingIndexes,
            )

            (adjustedRealRecords + filteredPending).sortedByDescending { it.timestamp }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // If something fails, return real records only
            realRecords
        }
    }

    private fun adjustMatchedRealRecords(
        realRecords: List<TransactionRecord>,
        pendingRecords: List<TransactionRecord>,
        matchedPendingByReal: Map<Int, PendingRealMatch>,
    ): List<TransactionRecord> {
        return realRecords.mapIndexed { realIndex, real ->
            val match = matchedPendingByReal[realIndex] ?: return@mapIndexed real
            val pending = pendingRecords.getOrNull(match.pendingIndex) as? PendingTransactionRecord
                ?: return@mapIndexed real

            real.withPendingDisplayAmount(pending, match.kind)
        }
    }

    private fun TransactionRecord.withPendingDisplayAmount(
        pending: PendingTransactionRecord,
        matchKind: PendingTransactionMatchKind,
    ): TransactionRecord {
        if (matchKind != PendingTransactionMatchKind.LitecoinMwebPegIn) {
            return this
        }

        val bitcoinRecord = this as? BitcoinTransactionRecord ?: return this
        return bitcoinRecord.withMainAmount(pending.amount.abs().negate())
    }

    private fun filterDuplicatedPending(
        pendingRecords: List<TransactionRecord>,
        realRecords: List<TransactionRecord>,
        matchedPendingIndexes: Set<Int>,
    ): List<TransactionRecord> {
        val duplicatePendingUids = realRecords
            .filterIsInstance<PendingTransactionRecord>()
            .mapTo(HashSet()) { it.uid }

        return pendingRecords.filterIndexed { index, pending ->
            pending !is PendingTransactionRecord ||
                (pending.uid !in duplicatePendingUids && index !in matchedPendingIndexes)
        }
    }

    private suspend fun markMatchedRealRecordsCreated(
        matchedPendingByReal: Map<Int, PendingRealMatch>,
        realRecords: List<TransactionRecord>,
        pendingRecords: List<TransactionRecord>,
    ) {
        val pendingIdsToDelete = mutableListOf<String>()

        matchedPendingByReal.forEach { (realIndex, match) ->
            if (match.confidence < CREATED_MARK_MIN_CONFIDENCE) {
                return@forEach
            }

            val pendingId = pendingRecords.getOrNull(match.pendingIndex)?.uid ?: return@forEach
            val realRecord = realRecords.getOrNull(realIndex) ?: return@forEach

            locallyCreatedTransactionRepository.markCreated(realRecord)
            pendingIdsToDelete.add(pendingId)
        }

        if (pendingIdsToDelete.isNotEmpty()) {
            pendingRepository.deleteByIds(pendingIdsToDelete)
        }
    }

    private fun matchedPendingByRealIndexes(
        pendingRecords: List<TransactionRecord>,
        realRecords: List<TransactionRecord>,
    ): Map<Int, PendingRealMatch> {
        val realCandidates = realRecords.withIndex()
            .filterNot { it.value is PendingTransactionRecord }

        val pendingCandidateMap = pendingRecords.mapIndexedNotNull { pendingIndex, record ->
            val pending = record as? PendingTransactionRecord ?: return@mapIndexedNotNull null
            val candidates = realCandidates.mapNotNull { (realIndex, real) ->
                val matchScore = pendingTransactionMatcher.matchScoreForRealRecord(pending, real)
                if (!matchScore.isMatch) {
                    return@mapNotNull null
                }

                PendingRealMatchCandidate(
                    realIndex = realIndex,
                    confidence = matchScore.confidence,
                    timestampDifference = abs(pending.timestamp - real.timestamp),
                    kind = matchScore.kind,
                )
            }.sortedWith(
                compareByDescending<PendingRealMatchCandidate> { it.confidence }
                    .thenBy { it.timestampDifference }
                    .thenBy { it.realIndex }
            )

            if (candidates.isEmpty()) {
                null
            } else {
                pendingIndex to candidates
            }
        }.toMap()

        val pendingIndexes = pendingCandidateMap.keys.sortedWith(
            compareBy<Int> { pendingCandidateMap.getValue(it).size }
                .thenByDescending { pendingCandidateMap.getValue(it).first().confidence }
                .thenBy { pendingCandidateMap.getValue(it).first().timestampDifference }
                .thenBy { it }
        )

        val matchedPendingByReal = mutableMapOf<Int, PendingRealMatch>()
        pendingIndexes.forEach { pendingIndex ->
            assignRealRecord(
                pendingIndex = pendingIndex,
                pendingCandidateMap = pendingCandidateMap,
                visitedRealIndexes = mutableSetOf(),
                matchedPendingByReal = matchedPendingByReal
            )
        }

        return matchedPendingByReal
    }

    private fun assignRealRecord(
        pendingIndex: Int,
        pendingCandidateMap: Map<Int, List<PendingRealMatchCandidate>>,
        visitedRealIndexes: MutableSet<Int>,
        matchedPendingByReal: MutableMap<Int, PendingRealMatch>,
    ): Boolean {
        val candidates = pendingCandidateMap[pendingIndex] ?: return false

        for (candidate in candidates) {
            if (!visitedRealIndexes.add(candidate.realIndex)) {
                continue
            }

            val assignedMatch = matchedPendingByReal[candidate.realIndex]
            if (assignedMatch == null || assignRealRecord(
                    pendingIndex = assignedMatch.pendingIndex,
                    pendingCandidateMap = pendingCandidateMap,
                    visitedRealIndexes = visitedRealIndexes,
                    matchedPendingByReal = matchedPendingByReal
                )
            ) {
                matchedPendingByReal[candidate.realIndex] = PendingRealMatch(
                    pendingIndex = pendingIndex,
                    confidence = candidate.confidence,
                    kind = candidate.kind,
                )
                return true
            }
        }

        return false
    }

    override fun clear() {
        coroutineScope.cancel()
    }
}

internal class StaleTransactionReadException : CancellationException("Transaction cache was invalidated")
