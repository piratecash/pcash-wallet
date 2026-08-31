package cash.p.terminal.modules.balance.token

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.ITransactionRecordRepository
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.RecordsBatch
import cash.p.terminal.modules.transactions.TransactionSyncStateRepository
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.transaction.TransactionSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves the serviceVersion guard in [TokenTransactionsService] is necessary.
 *
 * Reproduces the race: a batch emitted for filter A is being processed by
 * handleUpdatedRecords() (blocked on the synchronous NFT-metadata read) when the user
 * switches the filter. setTransactionType() bumps serviceVersion and clears the list.
 * When the stale batch resumes, the guard must discard it instead of writing filter-A
 * records over the freshly cleared list.
 *
 * Detection: getLastBlockInfo(source) is reached only after a batch passes the guard and
 * enters the item-building block. If stale batch A leaks, getLastBlockInfo is invoked with
 * sourceA; the guard prevents that. Batch B (current filter) is emitted afterwards as a
 * barrier — the sequential collector processes it only after batch A's handler returns, so
 * waiting for batch B guarantees batch A is fully drained before the assertion.
 */
class TokenTransactionsServiceServiceVersionTest : KoinTest {

    private val repository = mockk<ITransactionRecordRepository>(relaxed = true)
    private val rateRepository = mockk<TransactionsRateRepository>(relaxed = true)
    private val syncStateRepository = mockk<TransactionSyncStateRepository>(relaxed = true)
    private val adapterManager = mockk<TransactionAdapterManager>(relaxed = true)
    private val nftMetadataService = mockk<NftMetadataService>(relaxed = true)
    private val spamManager = mockk<SpamManager>(relaxed = true)
    private val wallet = mockk<Wallet>(relaxed = true)
    private val koinAdapterManager = mockk<IAdapterManager>(relaxed = true)

    private lateinit var repositoryItemsFlow: MutableSharedFlow<RecordsBatch>

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single { repository }
                single<IAdapterManager> { koinAdapterManager }
            }
        )
    }

    @Before
    fun setUp() {
        repositoryItemsFlow = MutableSharedFlow(replay = 0, extraBufferCapacity = 8)
        every { repository.itemsFlow } returns repositoryItemsFlow
        every { rateRepository.dataExpiredFlow } returns MutableSharedFlow()
        every { rateRepository.historicalRateFlow } returns MutableSharedFlow()
        every { rateRepository.getHistoricalRate(any()) } returns null
        every { syncStateRepository.lastBlockInfoFlow } returns MutableSharedFlow()
        every { syncStateRepository.syncingFlow } returns MutableStateFlow(false)
        // emptyMap never contains wallet.transactionSource -> handleInitialization() never runs
        every { adapterManager.adaptersReadyFlow } returns MutableStateFlow(emptyMap())
        every { nftMetadataService.assetsBriefMetadataFlow } returns MutableStateFlow(emptyMap())
        every { spamManager.shouldHide(any()) } returns false
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun handleUpdatedRecords_filterSwitchedWhileProcessing_guardDiscardsStaleBatch() = runBlocking {
        val sourceA = mockk<TransactionSource>(relaxed = true)
        val sourceB = mockk<TransactionSource>(relaxed = true)
        val recordA = mockRecord("a1", sourceA)
        val recordB = mockRecord("b1", sourceB)

        val metadataReached = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val batchBReached = CountDownLatch(1)
        var metadataCall = 0

        every { nftMetadataService.assetsBriefMetadata(any()) } answers {
            if (metadataCall++ == 0) {
                metadataReached.countDown()
                proceed.await(5, TimeUnit.SECONDS)
            }
            emptyMap()
        }

        val getLastBlockSources = Collections.synchronizedList(mutableListOf<TransactionSource>())
        coEvery { syncStateRepository.getLastBlockInfo(any()) } answers {
            val source = firstArg<TransactionSource>()
            getLastBlockSources.add(source)
            if (source == sourceB) batchBReached.countDown()
            null
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )
        service.start()

        // Ensure the itemsFlow collector is subscribed before emitting (replay = 0)
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        // Batch A arrives and blocks inside handleUpdatedRecords on the metadata read
        repositoryItemsFlow.emit(RecordsBatch(listOf(recordA)))
        assertTrue(
            "handleUpdatedRecords did not reach the metadata read",
            metadataReached.await(5, TimeUnit.SECONDS)
        )

        // User switches filter while batch A is mid-flight: bumps serviceVersion, clears list
        service.setTransactionType(FilterTransactionType.Incoming)

        // Release the stale batch A
        proceed.countDown()

        // Emit batch B (current filter) as a barrier: the sequential collector runs it only
        // after batch A's handler has fully returned.
        repositoryItemsFlow.emit(RecordsBatch(listOf(recordB)))
        assertTrue(
            "Batch B was never processed",
            batchBReached.await(5, TimeUnit.SECONDS)
        )

        // Batch A's handler has now completed. The guard must have discarded it, so
        // getLastBlockInfo was never invoked with sourceA.
        assertFalse(
            "Stale batch A leaked past the serviceVersion guard",
            getLastBlockSources.contains(sourceA)
        )

        service.clear()
    }

    @Test
    fun handleUpdatedRecords_nonEmptyBatch_publishesItemsBeforeMarkingLoaded() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val record = mockRecord("r1", source)

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )

        // getLastBlockInfo runs while building items inside _transactionItems.update {}. Capture
        // whether records were already marked loaded at that point: if so, the loaded flag flipped
        // before the items were published — the ordering that flashes "no transactions".
        var recordsLoadedDuringBuild: Boolean? = null
        val buildReached = CountDownLatch(1)
        coEvery { syncStateRepository.getLastBlockInfo(any()) } answers {
            recordsLoadedDuringBuild = service.recordsLoadedFlow.value
            buildReached.countDown()
            null
        }

        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        repositoryItemsFlow.emit(RecordsBatch(listOf(record)))
        assertTrue("Item building was never reached", buildReached.await(5, TimeUnit.SECONDS))

        // Items must be published before the loaded flag flips.
        assertEquals(false, recordsLoadedDuringBuild)

        // Sanity: the flag is still set once the batch is fully processed.
        waitUntil { service.recordsLoadedFlow.value }
        assertTrue(service.recordsLoadedFlow.value)

        service.clear()
    }

    @Test
    fun handleUpdatedRecords_initialEmptyClearThenRealBatch_clearDoesNotMarkLoaded() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val record = mockRecord("r1", source)

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )

        // Captured while the real first page is being built (inside _transactionItems.update, before
        // the loaded flag is set). The repository emits a synthetic empty list first to clear the UI
        // before the page loads; if that clear marked records as loaded, this would be true — the
        // ordering that flashes "no transactions" on an already-synced wallet during init.
        var recordsLoadedWhenRealBatchBuilt: Boolean? = null
        val buildReached = CountDownLatch(1)
        coEvery { syncStateRepository.getLastBlockInfo(any()) } answers {
            recordsLoadedWhenRealBatchBuilt = service.recordsLoadedFlow.value
            buildReached.countDown()
            null
        }

        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        // 1) synthetic empty clear, 2) real first page
        repositoryItemsFlow.emit(RecordsBatch(emptyList()))
        repositoryItemsFlow.emit(RecordsBatch(listOf(record)))

        assertTrue("Item building was never reached", buildReached.await(5, TimeUnit.SECONDS))

        // The clear must not have marked records as loaded.
        assertEquals(false, recordsLoadedWhenRealBatchBuilt)

        service.clear()
    }

    @Test
    fun handleInitialization_typeSelectedBeforeInit_initKeepsSelectedTypeNotAll() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        every { wallet.transactionSource } returns source

        // Controllable readiness: empty until we publish, so handleInitialization stays gated
        // on adaptersReadyFlow.first() until the user has already picked a filter.
        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(emptyMap())
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow

        val setTypes = Collections.synchronizedList(mutableListOf<FilterTransactionType>())
        every { repository.set(any(), any(), any(), any(), any()) } answers {
            setTypes.add(thirdArg())
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )
        service.start()

        // User taps Incoming before the transaction adapter is ready (init not run yet).
        service.setTransactionType(FilterTransactionType.Incoming)

        // Adapter becomes ready -> handleInitialization runs.
        adaptersFlow.value = mapOf(source to adapter)

        // Wait for handleInitialization to issue its set().
        waitUntil { setTypes.isNotEmpty() }

        // handleInitialization must load exactly the user's selection: one set() with Incoming,
        // never a reset to All.
        assertEquals(listOf(FilterTransactionType.Incoming), setTypes.toList())

        service.clear()
    }

    @Test
    fun setTransactionType_calledBeforeInit_recordsLoadedStaysFalseUntilRealBatch() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        val record = mockRecord("r1", source)
        every { wallet.transactionSource } returns source

        // Controllable readiness: empty until published, so handleInitialization stays gated.
        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(emptyMap())
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow

        // Mirrors the real repository.set(): always emits a synthetic clear, then a load result.
        // With no active adapters (pre-init) the result is empty; once adapters are ready it
        // returns the real page.
        every { repository.set(any(), any(), any(), any(), any()) } answers {
            repositoryItemsFlow.tryEmit(RecordsBatch(emptyList()))
            if (adaptersFlow.value.isEmpty()) {
                repositoryItemsFlow.tryEmit(RecordsBatch(emptyList()))
            } else {
                repositoryItemsFlow.tryEmit(RecordsBatch(listOf(record)))
            }
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )

        // Captured while the real first page is being built (inside _transactionItems.update, before
        // the loaded flag is set). Any pre-init empty batch must not have flipped it true by then.
        var recordsLoadedWhenRealBatchBuilt: Boolean? = null
        val buildReached = CountDownLatch(1)
        coEvery { syncStateRepository.getLastBlockInfo(any()) } answers {
            recordsLoadedWhenRealBatchBuilt = service.recordsLoadedFlow.value
            buildReached.countDown()
            null
        }

        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        // User taps a filter before init: a pre-init set() would emit two empty batches here.
        service.setTransactionType(FilterTransactionType.Incoming)

        // Adapter becomes ready -> handleInitialization issues the first real set().
        adaptersFlow.value = mapOf(source to adapter)

        assertTrue("Real batch was never built", buildReached.await(5, TimeUnit.SECONDS))

        // The pre-init empty batches must not have marked records as loaded before the real page.
        assertEquals(false, recordsLoadedWhenRealBatchBuilt)

        service.clear()
    }

    @Test
    fun handleInitialization_typeChangedDuringInitialLoad_reloadsRacedType() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        every { wallet.transactionSource } returns source

        // Adapter ready from the start so handleInitialization runs immediately.
        every { adapterManager.adaptersReadyFlow } returns
            MutableStateFlow(mapOf(source to adapter))

        val initSetStarted = CountDownLatch(1)
        val releaseInitSet = CountDownLatch(1)
        val incomingSetReached = CountDownLatch(1)
        var setCall = 0
        every { repository.set(any(), any(), any(), any(), any()) } answers {
            val type = thirdArg<FilterTransactionType>()
            if (setCall++ == 0) {
                // First set() is the initial load; block it to keep the init window open.
                initSetStarted.countDown()
                releaseInitSet.await(5, TimeUnit.SECONDS)
            }
            if (type == FilterTransactionType.Incoming) incomingSetReached.countDown()
            false
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )
        service.start()

        // The initial load is now in progress (blocked inside set()), before initialized = true.
        assertTrue("Initial load never started", initSetStarted.await(5, TimeUnit.SECONDS))

        // User switches filter during the initial load: adapters are ready, init not yet finished.
        service.setTransactionType(FilterTransactionType.Incoming)

        // Let the initial load finish.
        releaseInitSet.countDown()

        // After init, the service must reconcile the data layer to the raced selection.
        assertTrue(
            "Init did not reload the filter selected during the initial load",
            incomingSetReached.await(5, TimeUnit.SECONDS)
        )

        service.clear()
    }

    @Test
    fun start_adapterRemainsUnavailable_keepsLoadingWithoutInitializing() = runBlocking {
        val initializations = AtomicInteger(0)
        every { syncStateRepository.setTransactionWallets(any()) } answers {
            initializations.incrementAndGet()
            Unit
        }

        val service = startWithoutAdapter()
        Thread.sleep(LEGACY_ADAPTER_READY_TIMEOUT_WINDOW_MS)

        assertFalse(
            "Slow adapter initialization was reported as a failed history read",
            service.recordsLoadFailedFlow.value,
        )
        assertFalse(service.recordsLoadedFlow.value)
        assertEquals(
            "Initialization ran without an adapter and would answer \"no transactions\"",
            0,
            initializations.get()
        )

        service.clear()
    }

    @Test
    fun start_adapterArrivesAfterLegacyTimeout_initializesWithoutFailure() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        every { wallet.transactionSource } returns source
        every { repository.set(any(), any(), any(), any(), any(), any()) } returns true

        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(emptyMap())
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow

        val initializations = AtomicInteger(0)
        every { syncStateRepository.setTransactionWallets(any()) } answers {
            initializations.incrementAndGet()
            Unit
        }

        val service = createService()
        service.start()

        waitUntil { adaptersFlow.subscriptionCount.value >= 1 }
        Thread.sleep(LEGACY_ADAPTER_READY_TIMEOUT_WINDOW_MS)
        assertFalse(service.recordsLoadFailedFlow.value)
        assertEquals(0, initializations.get())

        adaptersFlow.value = mapOf(source to adapter)

        waitUntil { initializations.get() == 1 && !service.recordsLoadFailedFlow.value }
        assertEquals(1, initializations.get())

        service.clear()
    }

    @Test
    fun start_adapterRemoved_doesNotInitializeWithoutAdapter() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        every { wallet.transactionSource } returns source
        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(
            mapOf(source to mockk(relaxed = true))
        )
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow

        val initializations = AtomicInteger(0)
        every { syncStateRepository.setTransactionWallets(any()) } answers {
            initializations.incrementAndGet()
            Unit
        }

        val service = createService()
        service.start()
        waitUntil { initializations.get() == 1 }

        adaptersFlow.value = emptyMap()

        waitUntil(SETTLE_MS) { initializations.get() > 1 }
        assertEquals(
            "Losing the adapter re-ran initialization against an empty adapter map",
            1,
            initializations.get()
        )

        service.clear()
    }

    @Test
    fun start_adapterRemovedAndRestored_initializesAgain() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        every { wallet.transactionSource } returns source
        val adaptersFlow = MutableStateFlow(mapOf(source to adapter))
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow

        val initializations = AtomicInteger(0)
        every { syncStateRepository.setTransactionWallets(any()) } answers {
            initializations.incrementAndGet()
            Unit
        }

        val service = createService()
        service.start()
        waitUntil { initializations.get() == 1 }

        adaptersFlow.value = emptyMap()
        // StateFlow conflates: give the collector time to observe the gap, otherwise the returning
        // instance is indistinguishable from the one already initialized with.
        Thread.sleep(SETTLE_MS)
        adaptersFlow.value = mapOf(source to adapter)

        waitUntil { initializations.get() == 2 }
        assertEquals(
            "The same adapter instance coming back was swallowed as a duplicate",
            2,
            initializations.get()
        )

        service.clear()
    }

    @Test
    fun start_adapterDisappearsWhileObserverResubscribes_stillInitializes() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        every { wallet.transactionSource } returns source
        every { repository.set(any(), any(), any(), any(), any(), any()) } returns true

        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(
            mapOf(source to mockk(relaxed = true))
        )
        // A second collect means the observer let go of the first subscription. An adapter that
        // disappears in that gap is never seen again, and the screen waits forever for an
        // arrival that already happened.
        every { adapterManager.adaptersReadyFlow } returns CollectCountingStateFlow(adaptersFlow) {
            if (it >= 2) adaptersFlow.value = emptyMap()
        }

        val initializations = AtomicInteger(0)
        every { syncStateRepository.setTransactionWallets(any()) } answers {
            initializations.incrementAndGet()
            Unit
        }

        val service = createService()
        service.start()

        waitUntil(ADAPTER_WAIT_MS) { initializations.get() == 1 }
        assertEquals(
            "The observer resubscribed and lost the adapter that was already there",
            1,
            initializations.get()
        )

        service.clear()
    }

    @Test
    fun start_adapterRemovedRightAfterEmission_readsWhileTheAdapterIsStillThere() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        every { wallet.transactionSource } returns source

        val adaptersFlow = MutableStateFlow(mapOf(source to mockk<ITransactionsAdapter>(relaxed = true)))
        // The adapter disappears the instant the emission is accepted. A buffered hand-off would
        // run the read against an already empty map, and the repository resolves the adapter from
        // the manager, so that read finalizes an empty history for a wallet that has one.
        every { adapterManager.adaptersReadyFlow } returns ClearOnEmitStateFlow(adaptersFlow)

        val loads = AtomicInteger(0)
        val loadsWithoutAdapter = AtomicInteger(0)
        every { repository.set(any(), any(), any(), any(), any(), any()) } answers {
            if (!adaptersFlow.value.containsKey(source)) loadsWithoutAdapter.incrementAndGet()
            loads.incrementAndGet()
            true
        }

        val service = createService()
        service.start()

        waitUntil(ADAPTER_WAIT_MS) { loads.get() >= 1 }
        assertEquals("The initial read never happened", 1, loads.get())
        assertEquals(
            "The read ran after the adapter was already gone",
            0,
            loadsWithoutAdapter.get()
        )

        service.clear()
    }

    @Test
    fun handleUpdatedRecords_onlyEmptyBatchArrives_marksRecordsLoaded() = runBlocking {
        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )

        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        // An empty wallet reports its history in a single empty batch; nothing follows it.
        repositoryItemsFlow.emit(RecordsBatch(emptyList()))

        waitUntil { service.recordsLoadedFlow.value }
        assertTrue(
            "An empty wallet stays blocked on the sync placeholder forever",
            service.recordsLoadedFlow.value
        )

        service.clear()
    }

    @Test
    fun handleInitialization_adapterReplacedWithoutReload_keepsRecordsLoaded() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        every { wallet.transactionSource } returns source
        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(
            mapOf(source to mockk(relaxed = true))
        )
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow
        // Only the first set() reports a pending reload; the repeated one has nothing to reload.
        every { repository.set(any(), any(), any(), any(), any(), any()) } returnsMany listOf(true, false)

        val initializations = AtomicInteger(0)
        every { syncStateRepository.setTransactionWallets(any()) } answers {
            initializations.incrementAndGet()
            Unit
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )
        service.start()

        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 && initializations.get() == 1 }
        repositoryItemsFlow.emit(RecordsBatch(listOf(mockRecord("a1", source))))
        waitUntil { service.recordsLoadedFlow.value }

        adaptersFlow.value = mapOf(source to mockk(relaxed = true))
        waitUntil { initializations.get() == 2 }

        assertTrue(
            "Cached records were dropped back to the loading state without a reload to end it",
            service.recordsLoadedFlow.value
        )

        service.clear()
    }

    @Test
    fun handleUpdatedRecords_incompleteEmptyBatch_marksLoadFailedWithoutClaimingEmptyHistory() =
        runBlocking {
            val service = startAndAwaitSubscription()

            repositoryItemsFlow.emit(RecordsBatch(emptyList(), loadFailed = true))

            waitUntil { service.recordsLoadFailedFlow.value }
            assertTrue(service.recordsLoadFailedFlow.value)
            assertTrue(service.transactionItemsFlow.value.isEmpty())

            waitUntil(FALLBACK_WINDOW_MS) { service.recordsLoadedFlow.value }
            assertFalse(
                "A failed read was presented as an empty wallet",
                service.recordsLoadedFlow.value
            )

            service.clear()
        }

    @Test
    fun handleUpdatedRecords_firstBatchFails_thenEmptySuccess_marksRecordsLoaded() = runBlocking {
        val service = startAndAwaitSubscription()

        repositoryItemsFlow.emit(RecordsBatch(emptyList(), loadFailed = true))
        waitUntil { service.recordsLoadFailedFlow.value }

        repositoryItemsFlow.emit(RecordsBatch(emptyList()))

        waitUntil { service.recordsLoadedFlow.value }
        assertTrue(
            "The retried empty history was swallowed as the synthetic initial clear",
            service.recordsLoadedFlow.value
        )
        assertFalse(service.recordsLoadFailedFlow.value)

        service.clear()
    }

    @Test
    fun handleUpdatedRecords_failedBatchWithPublishedItems_keepsItems() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val service = startAndAwaitSubscription()

        repositoryItemsFlow.emit(RecordsBatch(listOf(mockRecord("r1", source))))
        waitUntil { service.recordsLoadedFlow.value }

        repositoryItemsFlow.emit(RecordsBatch(emptyList(), loadFailed = true))

        waitUntil { service.recordsLoadFailedFlow.value }
        assertEquals(
            "A failed refresh wiped the history it could not re-read",
            listOf("r1"),
            service.transactionItemsFlow.value.map { it.record.uid }
        )

        service.clear()
    }

    @Test
    fun setTransactionType_duringEmptyBatchFallbackWindow_keepsRecordsUnloaded() = runBlocking {
        val service = startAndAwaitSubscription()

        // The synthetic initial clear schedules the fallback that would mark records loaded.
        repositoryItemsFlow.emit(RecordsBatch(emptyList()))
        Thread.sleep(SETTLE_MS)

        service.setTransactionType(FilterTransactionType.Incoming)

        waitUntil(FALLBACK_WINDOW_MS) { service.recordsLoadedFlow.value }
        assertFalse(
            "A stale fallback closed the loading window of a filter that never loaded",
            service.recordsLoadedFlow.value
        )

        service.clear()
    }

    @Test
    fun reload_beforeInitialization_requestsAdapterRefreshInsteadOfRepositoryRead() = runBlocking {
        val service = startWithoutAdapter()

        service.reload()

        verify { koinAdapterManager.refreshAdapters(listOf(wallet)) }
        verify(exactly = 0) { repository.reload() }

        service.clear()
    }

    @Test
    fun reload_afterInitializationWithAdapterGone_requestsAdapterRefreshInsteadOfRepositoryRead() =
        runBlocking {
            val source = mockk<TransactionSource>(relaxed = true)
            val adapter = mockk<ITransactionsAdapter>(relaxed = true)
            val replacement = mockk<ITransactionsAdapter>(relaxed = true)
            every { wallet.transactionSource } returns source
            val adaptersFlow = MutableStateFlow(mapOf(source to adapter))
            every { adapterManager.adaptersReadyFlow } returns adaptersFlow
            every { adapterManager.getAdapter(source) } returns adapter

            val initializations = AtomicInteger(0)
            every { syncStateRepository.setTransactionWallets(any()) } answers {
                initializations.incrementAndGet()
                Unit
            }

            val service = createService()
            service.start()
            waitUntil { initializations.get() == 1 }
            // The counter is incremented at the START of handleInitialization(), so it does not
            // prove `initialized` was assigned. The collector is sequential, so a SECOND
            // initialization can only begin after the first one returned: that is the real barrier.
            adaptersFlow.value = mapOf(source to replacement)
            waitUntil { initializations.get() == 2 }
            assertEquals(
                "The service never finished its first initialization",
                2,
                initializations.get()
            )

            // The adapter is gone by the time the user taps Retry. A repository reload would
            // iterate an empty adapter map and answer "no transactions" again, and the repository
            // cannot recreate what the manager no longer has.
            adaptersFlow.value = emptyMap()
            every { adapterManager.getAdapter(source) } returns null

            service.reload()

            verify { koinAdapterManager.refreshAdapters(listOf(wallet)) }
            verify(exactly = 0) { repository.reload() }

            service.clear()
        }

    @Test
    fun setTransactionType_beforeInitialization_keepsLoading() = runBlocking {
        val service = startWithoutAdapter()

        service.setTransactionType(FilterTransactionType.Incoming)

        assertFalse(service.recordsLoadFailedFlow.value)
        assertFalse(service.recordsLoadedFlow.value)

        service.clear()
    }

    @Test
    fun setSearchQuery_beforeInitialization_keepsLoading() = runBlocking {
        val service = startWithoutAdapter()

        service.setSearchQuery("query")

        assertFalse(service.recordsLoadFailedFlow.value)
        assertFalse(service.recordsLoadedFlow.value)

        service.clear()
    }

    private fun createService() = TokenTransactionsService(
        wallet = wallet,
        rateRepository = rateRepository,
        transactionSyncStateRepository = syncStateRepository,
        transactionAdapterManager = adapterManager,
        nftMetadataService = nftMetadataService,
        spamManager = spamManager,
    )

    private fun startAndAwaitSubscription(): TokenTransactionsService {
        val service = createService()
        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }
        return service
    }

    private fun startWithoutAdapter(): TokenTransactionsService {
        every { wallet.transactionSource } returns mockk<TransactionSource>(relaxed = true)
        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(emptyMap())
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow
        val service = createService()
        service.start()
        waitUntil { adaptersFlow.subscriptionCount.value >= 1 }
        return service
    }

    private companion object {
        /** Wait budget for asynchronous service operations. */
        const val ADAPTER_WAIT_MS = 10_000L

        /** Long enough to cross the removed wall-clock failure boundary. */
        const val LEGACY_ADAPTER_READY_TIMEOUT_WINDOW_MS = 3_200L

        /** Outlives the service's empty-batch fallback delay. */
        const val FALLBACK_WINDOW_MS = 1_000L

        /** Long enough for the IO collector to observe an emission that has no side effect. */
        const val SETTLE_MS = 200L
    }
}

/** Reports every collect, so a subscription that is released and retaken becomes observable. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class CollectCountingStateFlow(
    private val delegate: MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>,
    private val onCollect: (Int) -> Unit,
) : StateFlow<Map<TransactionSource, ITransactionsAdapter>> by delegate {
    private val collects = AtomicInteger(0)

    override suspend fun collect(
        collector: FlowCollector<Map<TransactionSource, ITransactionsAdapter>>
    ): Nothing {
        onCollect(collects.incrementAndGet())
        delegate.collect(collector)
    }
}

/** Drops the adapter as soon as the emission is accepted, exposing any buffered hand-off. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class ClearOnEmitStateFlow(
    private val delegate: MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>,
) : StateFlow<Map<TransactionSource, ITransactionsAdapter>> by delegate {

    override suspend fun collect(
        collector: FlowCollector<Map<TransactionSource, ITransactionsAdapter>>
    ): Nothing {
        collector.emit(delegate.value)
        delegate.value = emptyMap()
        delegate.collect(collector)
    }
}
