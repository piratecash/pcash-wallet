package cash.p.terminal.modules.transactions

import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.balance.token.mockRecord
import cash.p.terminal.modules.balance.token.waitUntil
import cash.p.terminal.wallet.transaction.TransactionSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.util.concurrent.atomic.AtomicInteger

class TransactionsServiceSearchPagingTest : KoinTest {

    private val repository = mockk<ITransactionRecordRepository>(relaxed = true)
    private val rateRepository = mockk<TransactionsRateRepository>(relaxed = true)
    private val syncStateRepository = mockk<TransactionSyncStateRepository>(relaxed = true)
    private val spamManager = mockk<SpamManager>(relaxed = true)
    private val source = mockk<TransactionSource>(relaxed = true)
    private val loadNextCount = AtomicInteger(0)
    private lateinit var repositoryItemsFlow: MutableSharedFlow<RecordsBatch>

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(module { single { repository } })
    }

    @Before
    fun setUp() {
        repositoryItemsFlow = MutableSharedFlow(replay = 0, extraBufferCapacity = 8)
        every { repository.itemsFlow } returns repositoryItemsFlow
        every { repository.set(any(), any(), any(), any(), any(), any()) } returns true
        every { repository.loadNext() } answers { loadNextCount.incrementAndGet(); Unit }
        every { rateRepository.dataExpiredFlow } returns MutableSharedFlow()
        every { rateRepository.historicalRateFlow } returns MutableSharedFlow()
        every { syncStateRepository.lastBlockInfoFlow } returns MutableSharedFlow()
        every { syncStateRepository.syncingFlow } returns MutableStateFlow(false)
        every { spamManager.shouldHide(any()) } returns false
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun handleUpdatedRecords_emptySearchBatchNotExhausted_loadsNextAndKeepsScanning() = runBlocking {
        val service = searchingService()

        repositoryItemsFlow.emit(RecordsBatch(emptyList(), searchCompleted = true, searchExhausted = false))
        waitUntil { loadNextCount.get() >= 1 }

        assertEquals(1, loadNextCount.get())
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)
        assertTrue(service.transactionItemsFlow.value.isEmpty())
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_emptySearchBatchExhausted_finishesEmpty() = runBlocking {
        val service = searchingService()

        repositoryItemsFlow.emit(RecordsBatch(emptyList(), searchCompleted = true, searchExhausted = true))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Finished }

        assertEquals(SearchScanState.Finished, service.searchScanStateFlow.value)
        assertTrue(service.transactionItemsFlow.value.isEmpty())
        verify(exactly = 0) { repository.loadNext() }
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_repeatedEmptySearchBatches_eachLoadsNext() = runBlocking {
        val service = searchingService()

        repeat(3) { page ->
            repositoryItemsFlow.emit(RecordsBatch(emptyList(), searchCompleted = true, searchExhausted = false))
            waitUntil { loadNextCount.get() > page }
        }

        assertEquals(3, loadNextCount.get())
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_searchBatchWithoutNewRecord_loadsNextAndKeepsShownRecords() = runBlocking {
        val service = searchingService()
        val match = mockRecord("match-1", mockk<TransactionSource>(relaxed = true))

        repositoryItemsFlow.emit(RecordsBatch(listOf(match), searchCompleted = true, searchExhausted = false))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Finished }
        repositoryItemsFlow.emit(RecordsBatch(listOf(match), searchCompleted = true, searchExhausted = false))
        waitUntil { loadNextCount.get() >= 1 }

        assertEquals(1, loadNextCount.get())
        assertEquals(listOf("match-1"), service.transactionItemsFlow.value.map { it.record.uid })
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_noResultPastAutoScanLimit_pausesAndStopsReading() = runBlocking {
        val service = searchingService()

        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW / 2))
        waitUntil { loadNextCount.get() >= 1 }
        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW))
        waitUntil { service.searchScanStateFlow.value is SearchScanState.Paused }
        service.loadNext()

        assertEquals(SearchScanState.Paused(WINDOW), service.searchScanStateFlow.value)
        assertEquals(1, loadNextCount.get())
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_firstFullPageWithoutResult_pauses() = runBlocking {
        val service = searchingService()

        repositoryItemsFlow.emit(searchPage(scannedCount = 5_000))
        waitUntil { service.searchScanStateFlow.value is SearchScanState.Paused }

        assertEquals(SearchScanState.Paused(5_000), service.searchScanStateFlow.value)
        assertEquals(0, loadNextCount.get())
        service.clear()
    }

    @Test
    fun searchFurther_afterPause_scansNextWindow() = runBlocking {
        val service = searchingService()
        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW))
        waitUntil { service.searchScanStateFlow.value is SearchScanState.Paused }

        service.searchFurther()
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)
        waitUntil { loadNextCount.get() >= 1 }
        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW + WINDOW / 2))
        waitUntil { loadNextCount.get() >= 2 }
        repositoryItemsFlow.emit(searchPage(scannedCount = 2 * WINDOW))
        waitUntil { service.searchScanStateFlow.value is SearchScanState.Paused }

        assertEquals(SearchScanState.Paused(2 * WINDOW), service.searchScanStateFlow.value)
        assertEquals(2, loadNextCount.get())
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_resultInsideWindow_restartsWindow() = runBlocking {
        val service = searchingService()
        val match = listOf(mockRecord("match-1", mockk<TransactionSource>(relaxed = true)))

        repositoryItemsFlow.emit(searchPage(scannedCount = FOUND_AT, records = match))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Finished }
        repositoryItemsFlow.emit(searchPage(scannedCount = FOUND_AT + WINDOW / 2, records = match))
        waitUntil { loadNextCount.get() >= 1 }
        repositoryItemsFlow.emit(searchPage(scannedCount = FOUND_AT + WINDOW, records = match))
        waitUntil { service.searchScanStateFlow.value is SearchScanState.Paused }

        val paused = service.searchScanStateFlow.value as SearchScanState.Paused
        assertEquals(FOUND_AT + WINDOW, paused.scannedCount)
        assertEquals(listOf("match-1"), service.transactionItemsFlow.value.map { it.record.uid })
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_exhaustedBeforeAutoScanLimit_finishes() = runBlocking {
        val service = searchingService()

        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW / 2, exhausted = true))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Finished }

        assertEquals(SearchScanState.Finished, service.searchScanStateFlow.value)
        assertEquals(0, loadNextCount.get())
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_refreshedBatchDropsMatch_removesRowAndPauses() = runBlocking {
        val service = searchingService()
        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW / 2, records = listOf(mockRecord("match-1", source))))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Finished }

        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW / 2 + WINDOW))
        waitUntil { service.searchScanStateFlow.value is SearchScanState.Paused }

        assertEquals(SearchScanState.Paused(WINDOW / 2 + WINDOW), service.searchScanStateFlow.value)
        assertTrue(service.transactionItemsFlow.value.isEmpty())
        assertEquals(0, loadNextCount.get())
        service.clear()
    }

    @Test
    fun handleUpdatedRecords_refreshedBatchDropsMatchInsideWindow_removesRowAndScansOn() = runBlocking {
        val service = searchingService()
        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW / 2, records = listOf(mockRecord("match-1", source))))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Finished }

        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW))
        waitUntil { loadNextCount.get() >= 1 }

        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)
        assertTrue(service.transactionItemsFlow.value.isEmpty())
        service.clear()
    }

    @Test
    fun reload_whilePaused_scansFreshWindow() = runBlocking {
        val service = searchingService()
        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW))
        waitUntil { service.searchScanStateFlow.value is SearchScanState.Paused }
        service.searchFurther()
        repositoryItemsFlow.emit(searchPage(scannedCount = 2 * WINDOW))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Paused(2 * WINDOW) }

        service.reload()
        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW / 2, sessionId = 2))
        waitUntil { loadNextCount.get() >= 2 }
        repositoryItemsFlow.emit(searchPage(scannedCount = WINDOW, sessionId = 2))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Paused(WINDOW) }

        assertEquals(2, loadNextCount.get())
        assertEquals(SearchScanState.Paused(WINDOW), service.searchScanStateFlow.value)
        service.clear()
    }

    private fun searchPage(
        scannedCount: Int,
        records: List<TransactionRecord> = emptyList(),
        exhausted: Boolean = false,
        sessionId: Long = 1,
    ) = RecordsBatch(records, true, exhausted, searchScannedCount = scannedCount, searchSessionId = sessionId)

    private fun searchingService(): TransactionsService {
        val service = TransactionsService(rateRepository, syncStateRepository, spamManager)
        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }
        service.set(emptyList(), null, FilterTransactionType.All, null, null, searchQuery = "needle")
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)
        return service
    }
}

private const val WINDOW = SearchPaging.AUTO_SCAN_LIMIT

// Scanned count of the batch that finds the match, inside the first window.
private const val FOUND_AT = WINDOW - 1_000
