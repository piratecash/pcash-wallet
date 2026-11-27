package cash.p.terminal.modules.transactions

import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.modules.contacts.model.ContactAddress
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Verifies that TransactionRecordRepository discards stale data
 * from account A when switching to account B with identical filters.
 *
 * Models the actual race through the real code path:
 *   loadItems() -> TransactionAdapterWrapper.get() -> ITransactionsAdapter.getTransactions()
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRecordRepositoryWalletSwitchTest {

    private val searchToken = createToken()
    private val searchSource = createSource("account-1", searchToken.blockchain)

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun switchAccountWithSameFilters_slowGetTransactions_oldResultDiscarded() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        startKoin {
            modules(module {
                single { mockk<cash.p.terminal.core.managers.CoinManager>(relaxed = true) }
            })
        }

        val accountA = Account(
            id = "account-A", name = "A",
            type = mockk(relaxed = true), origin = AccountOrigin.Created, level = 0
        )
        val accountB = Account(
            id = "account-B", name = "B",
            type = mockk(relaxed = true), origin = AccountOrigin.Created, level = 0
        )
        val blockchainA = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
        val blockchainB = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null)
        val sourceA = TransactionSource(blockchainA, accountA, null)
        val sourceB = TransactionSource(blockchainB, accountB, null)
        val walletA = TransactionWallet(token = null, source = sourceA, badge = null)
        val walletB = TransactionWallet(token = null, source = sourceB, badge = null)

        val recordA = mockk<TransactionRecord>(relaxed = true) {
            every { uid } returns "record-A"
            every { source } returns sourceA
            every { timestamp } returns 1000L
            every { spam } returns false
            every { compareTo(any()) } returns 0
        }
        val recordB = mockk<TransactionRecord>(relaxed = true) {
            every { uid } returns "record-B"
            every { source } returns sourceB
            every { timestamp } returns 2000L
            every { spam } returns false
            every { compareTo(any()) } returns 0
        }

        val adapterAGate = CompletableDeferred<Unit>()

        val adapterA = mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery { getTransactions(any(), any(), any(), any(), any()) } coAnswers {
                adapterAGate.await()
                listOf(recordA)
            }
            every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
            every { getTransactionUrl(any()) } returns ""
        }

        val adapterB = mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery { getTransactions(any(), any(), any(), any(), any()) } returns listOf(recordB)
            every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
            every { getTransactionUrl(any()) } returns ""
        }

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(sourceA) } returns adapterA
            every { getAdapter(sourceB) } returns adapterB
        }

        val pendingRepository = mockk<PendingTransactionRepository>(relaxed = true) {
            every { getActivePendingFlow(any()) } returns emptyFlow()
            coEvery { getPendingForWallet(any()) } returns emptyList()
        }

        val repository = TransactionRecordRepository(
            adapterManager = adapterManager,
            swapProviderTransactionsStorage = mockk(relaxed = true),
            pendingRepository = pendingRepository,
            pendingConverter = mockk(relaxed = true),
            pendingTransactionMatcher = PendingTransactionMatcher(),
            searchMatcher = testSearchMatcher(),
            searchVisibility = testSearchVisibility(),
            locallyCreatedTransactionRepository = relaxedLocallyCreatedRepository(),
            dispatcherProvider = TestDispatcherProvider(testDispatcher, this)
        )

        val emissions = mutableListOf<List<TransactionRecord>>()
        val collectorJob = launch(testDispatcher) {
            repository.itemsFlow.collect { batch ->
                emissions.add(batch.records)
            }
        }

        // Step 1: Set account A -> getTransactions() suspends on gate
        repository.setAndReload(
            transactionWallets = listOf(walletA),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        // Step 2: While A is still suspended, switch to B (same filters)
        repository.setAndReload(
            transactionWallets = listOf(walletB),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        // Step 3: Open the gate — adapter A's getTransactions() returns
        adapterAGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(
            "record-B from new account should appear in emissions, " +
                "but got: ${emissions.map { it.map { r -> r.uid } }}",
            emissions.any { list -> list.any { it.uid == "record-B" } }
        )

        assertTrue(
            "record-A from old account should never appear in emissions, " +
                "but got: ${emissions.map { it.map { r -> r.uid } }}",
            emissions.none { list -> list.any { it.uid == "record-A" } }
        )

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun switchContactInSwapFilter_extraSwapAdaptersUseNewContact() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        startKoin {
            modules(module {
                single { mockk<cash.p.terminal.core.managers.CoinManager>(relaxed = true) }
            })
        }

        val token = createToken()
        val source = createSource(accountId = "account-1", blockchain = token.blockchain)
        val wallet = TransactionWallet(token = token, source = source, badge = null)
        val contactA = createContact(uid = "contact-A", blockchain = token.blockchain, address = "bc1-contact-a")
        val contactB = createContact(uid = "contact-B", blockchain = token.blockchain, address = "bc1-contact-b")
        val recordA = createBitcoinOutgoingRecord(
            token = token,
            source = source,
            uid = "record-A",
            timestamp = 1_715_000_000,
            amount = BigDecimal("-0.00000563"),
            toAddress = contactA.addresses.first().address,
        )
        val recordB = createBitcoinOutgoingRecord(
            token = token,
            source = source,
            uid = "record-B",
            timestamp = 1_715_000_010,
            amount = BigDecimal("-0.00000703"),
            toAddress = contactB.addresses.first().address,
        )

        val adapter = mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery {
                getTransactions(any(), token, any(), FilterTransactionType.Swap, any())
            } returns emptyList()
            coEvery {
                getTransactions(any(), token, any(), FilterTransactionType.All, contactA.addresses.first().address)
            } returns listOf(recordA)
            coEvery {
                getTransactions(any(), token, any(), FilterTransactionType.All, contactB.addresses.first().address)
            } returns listOf(recordB)
            every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
            every { getTransactionUrl(any()) } returns ""
        }

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(source) } returns adapter
        }

        val pendingRepository = mockk<PendingTransactionRepository>(relaxed = true) {
            every { getActivePendingFlow(any()) } returns emptyFlow()
            coEvery { getPendingForWallet(any()) } returns emptyList()
        }

        val swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
            every { getByOutgoingRecordUid("record-A") } returns mockk(relaxed = true)
            every { getByOutgoingRecordUid("record-B") } returns mockk(relaxed = true)
            every { observeAll() } returns emptyFlow()
        }

        val repository = TransactionRecordRepository(
            adapterManager = adapterManager,
            swapProviderTransactionsStorage = swapProviderTransactionsStorage,
            pendingRepository = pendingRepository,
            pendingConverter = mockk(relaxed = true),
            pendingTransactionMatcher = PendingTransactionMatcher(),
            searchMatcher = testSearchMatcher(swapProviderTransactionsStorage),
            searchVisibility = testSearchVisibility(swapProviderTransactionsStorage),
            locallyCreatedTransactionRepository = relaxedLocallyCreatedRepository(),
            dispatcherProvider = TestDispatcherProvider(testDispatcher, this)
        )

        val emissions = mutableListOf<List<String>>()
        val collectorJob = launch(testDispatcher) {
            repository.itemsFlow.collect { batch ->
                emissions.add(batch.records.map { it.uid })
            }
        }

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = null,
            transactionType = FilterTransactionType.Swap,
            blockchain = null,
            contact = contactA,
            searchQuery = null,
        )
        advanceUntilIdle()

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = null,
            transactionType = FilterTransactionType.Swap,
            blockchain = null,
            contact = contactB,
            searchQuery = null,
        )
        advanceUntilIdle()

        assertTrue(
            "record-A should appear for the first contact, but got: $emissions",
            emissions.any { it == listOf("record-A") }
        )
        assertEquals(listOf("record-B"), emissions.last())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun swapFilter_extraSwapAdapterUpdate_reloadsSwapProviderRecords() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val fixture = createSwapFilterFixture("swap-outgoing-record")
        val outgoingUpdates = MutableSharedFlow<List<TransactionRecord>>(extraBufferCapacity = 1)
        var outgoingRecords = emptyList<TransactionRecord>()
        val adapter = swapFilterAdapter(fixture.token, { outgoingRecords }, outgoingUpdates)
        val swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
            every { getByOutgoingRecordUid(fixture.record.uid) } returns mockk(relaxed = true)
            every { observeAll() } returns emptyFlow()
        }
        val repository = createRepository(
            adapterManager = adapterManager(fixture.source, adapter),
            dispatcher = testDispatcher,
            scope = this,
            swapProviderTransactionsStorage = swapProviderTransactionsStorage,
        )

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.setSwapWallet(fixture.wallet)
        advanceUntilIdle()

        outgoingRecords = listOf(fixture.record)
        outgoingUpdates.emit(listOf(fixture.record))
        advanceUntilIdle()

        assertTrue(
            "extra outgoing adapter update should reload swap provider records, but got: $emissions",
            emissions.any { it == listOf(fixture.record.uid) }
        )

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun swapFilter_swapProviderStorageUpdate_reloadsPreviouslyUnmatchedOutgoingRecord() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val fixture = createSwapFilterFixture("late-matched-record")
        val swapProviderUpdates = MutableSharedFlow<List<SwapProviderTransaction>>(extraBufferCapacity = 1)
        var matchedSwap: SwapProviderTransaction? = null

        val adapter = swapFilterAdapter(fixture.token, { listOf(fixture.record) })
        val swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
            every { getByOutgoingRecordUid(fixture.record.uid) } answers { matchedSwap }
            every { getByTokenIn(any(), any(), any()) } returns null
            every { observeAll() } returns swapProviderUpdates
        }
        val repository = createRepository(
            adapterManager = adapterManager(fixture.source, adapter),
            dispatcher = testDispatcher,
            scope = this,
            swapProviderTransactionsStorage = swapProviderTransactionsStorage,
        )

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.setSwapWallet(fixture.wallet)
        advanceUntilIdle()

        assertTrue(
            "record should not be visible before swap provider row is matched, but got: $emissions",
            emissions.none { it.contains(fixture.record.uid) }
        )

        val swap = mockk<SwapProviderTransaction>(relaxed = true)
        matchedSwap = swap
        swapProviderUpdates.emit(listOf(swap))
        advanceUntilIdle()

        assertEquals(listOf(fixture.record.uid), emissions.last())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_providerStatusChangesOnAllTab_reloadsMatchingRecord() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()

        val fixture = providerSearchFixture()
        val repository = createRepository(
            adapterManager = adapterManager(fixture.source, pagedAdapter(listOf(fixture.record))),
            dispatcher = testDispatcher,
            scope = this,
            swapProviderTransactionsStorage = fixture.storage,
        )
        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.searchAllTab(fixture.wallet, searchQuery = "процесс обмена")
        advanceUntilIdle()
        assertTrue(
            "a waiting swap must not match the exchanging title, but got: $emissions",
            emissions.none { it.contains(fixture.record.uid) }
        )

        fixture.storedSwap = testProviderSwap(TransactionStatusEnum.EXCHANGING, fixture.record.uid)
        fixture.updates.emit(listOf(fixture.storedSwap))
        advanceUntilIdle()

        assertEquals(listOf(fixture.record.uid), emissions.last())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_providerNameOnAllTab_reloadsWhenStorageMakesRecordMatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()

        val fixture = providerSearchFixture()
        val changeNowSwap = fixture.storedSwap
        fixture.storedSwap = changeNowSwap.copy(provider = SwapProvider.EXOLIX)
        val repository = createRepository(
            adapterManager = adapterManager(fixture.source, pagedAdapter(listOf(fixture.record))),
            dispatcher = testDispatcher,
            scope = this,
            swapProviderTransactionsStorage = fixture.storage,
        )
        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.searchAllTab(fixture.wallet, searchQuery = "changenow")
        advanceUntilIdle()
        assertTrue(
            "an Exolix swap must not match a ChangeNow search, but got: $emissions",
            emissions.none { it.contains(fixture.record.uid) }
        )

        fixture.storedSwap = changeNowSwap
        // The newest-rows payload need not contain the changed swap.
        fixture.updates.emit(emptyList())
        advanceUntilIdle()

        assertEquals(listOf(fixture.record.uid), emissions.last())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_notInStatusOrProviderTitleOnAllTab_storageChangeKeepsResultWithoutSwapLookups() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()

        val fixture = providerSearchFixture()
        val repository = createRepository(
            adapterManager = adapterManager(fixture.source, pagedAdapter(listOf(fixture.record))),
            dispatcher = testDispatcher,
            scope = this,
            swapProviderTransactionsStorage = fixture.storage,
        )
        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.searchAllTab(fixture.wallet, searchQuery = "provider-deposit")
        advanceUntilIdle()
        fixture.updates.emit(listOf(fixture.storedSwap))
        advanceUntilIdle()

        assertEquals(listOf(fixture.record.uid), emissions.last())
        verify(exactly = 0) { fixture.storage.getByOutgoingRecordUid(any()) }

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_matchAfterFirstUiPage_emitsOlderMatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val source = createSource(accountId = "account-1", blockchain = token.blockchain)
        val wallet = TransactionWallet(token = token, source = source, badge = null)
        val records = (0 until 25).map { index ->
            createBitcoinOutgoingRecord(
                token = token,
                source = source,
                uid = "record-$index",
                timestamp = 1_715_000_000L - index,
                amount = BigDecimal("-0.00000563"),
                toAddress = if (index == 24) "needle-address" else "other-address-$index",
            )
        }
        val repository = createRepository(
            adapterManager = adapterManager(source, simpleAdapter(records)),
            dispatcher = testDispatcher,
            scope = this,
        )

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = "needle",
        )
        advanceUntilIdle()

        assertEquals(listOf("record-24"), emissions.last())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_matchAfterFirstSearchBatch_emitsOlderMatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val source = createSource(accountId = "account-1", blockchain = token.blockchain)
        val wallet = TransactionWallet(token = token, source = source, badge = null)
        val records = (0 until 150).map { index ->
            createBitcoinOutgoingRecord(
                token = token,
                source = source,
                uid = "record-$index",
                timestamp = 1_715_000_000L - index,
                amount = BigDecimal("-0.00000563"),
                toAddress = if (index == 149) "needle-address" else "other-address-$index",
            )
        }
        val repository = createRepository(
            adapterManager = adapterManager(source, pagedAdapter(records)),
            dispatcher = testDispatcher,
            scope = this,
        )

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = "needle",
        )
        advanceUntilIdle()

        assertEquals(listOf("record-149"), emissions.last())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_swapFilter_includesExtraSwapAdapters() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val fixture = createSwapFilterFixture("swap-search-record")
        val adapter = swapFilterAdapter(fixture.token, { listOf(fixture.record) })
        val swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
            every { getByOutgoingRecordUid(fixture.record.uid) } returns mockk(relaxed = true)
            every { observeAll() } returns emptyFlow()
        }
        val repository = createRepository(
            adapterManager = adapterManager(fixture.source, adapter),
            dispatcher = testDispatcher,
            scope = this,
            swapProviderTransactionsStorage = swapProviderTransactionsStorage,
        )

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(fixture.wallet),
            wallet = null,
            transactionType = FilterTransactionType.Swap,
            blockchain = null,
            contact = null,
            searchQuery = "provider",
        )
        advanceUntilIdle()

        assertEquals(listOf(fixture.record.uid), emissions.last())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_oneSourceThrows_stillEmitsTerminalBatchWithOtherSourceMatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        // The throwing source is scanned first; without the failure being absorbed the
        // sequential scan would abort here and the healthy source would never be reached,
        // leaving the search without its terminal emission.
        val throwingSource = createSource("account-1", Blockchain(BlockchainType.Ethereum, "Ethereum", null))
        val matchingSource = createSource("account-1", token.blockchain)
        val throwingWallet = TransactionWallet(token = null, source = throwingSource, badge = null)
        val matchingWallet = TransactionWallet(token = token, source = matchingSource, badge = null)

        val match = createBitcoinOutgoingRecord(
            token = token,
            source = matchingSource,
            uid = "record-match",
            timestamp = 1_715_000_000L,
            amount = BigDecimal("-0.00000563"),
            toAddress = "needle-address",
        )

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(throwingSource) } returns throwingAdapter()
            every { getAdapter(matchingSource) } returns simpleAdapter(listOf(match))
        }

        val repository = createRepository(adapterManager, testDispatcher, this)

        val emissions = mutableListOf<RecordsBatch>()
        val collectorJob = launch(testDispatcher) {
            repository.itemsFlow.collect { emissions.add(it) }
        }

        repository.setAndReload(
            transactionWallets = listOf(throwingWallet, matchingWallet),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = "needle",
        )
        advanceUntilIdle()

        val terminalBatch = emissions.lastOrNull { it.searchCompleted }
        assertTrue(
            "a failing source must not strand the scan: a terminal search batch is expected, " +
                "but got: ${emissions.map { it.records.map { r -> r.uid } to it.searchCompleted }}",
            terminalBatch != null
        )
        assertEquals(listOf("record-match"), terminalBatch?.records?.map { it.uid })

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_newerMatchBuriedInOtherSource_isNotDroppedByEarlyStop() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val sourceWithOldMatches = createSource("account-old", token.blockchain)
        val sourceWithBuriedMatch = createSource("account-buried", token.blockchain)
        val walletOld = TransactionWallet(token = token, source = sourceWithOldMatches, badge = null)
        val walletBuried = TransactionWallet(token = token, source = sourceWithBuriedMatch, badge = null)

        // Source A: a full page (20) of matches, all older than the buried one.
        val oldMatches = (0 until 20).map { i ->
            searchRecord(token, sourceWithOldMatches, "old-$i", 1_000L - i, "needle-address")
        }

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(sourceWithOldMatches) } returns simpleAdapter(oldMatches)
            every { getAdapter(sourceWithBuriedMatch) } returns buriedMatchAdapter(token, sourceWithBuriedMatch)
        }

        val repository = createRepository(adapterManager, testDispatcher, this)

        val emissions = mutableListOf<RecordsBatch>()
        val collectorJob = launch(testDispatcher) {
            repository.itemsFlow.collect { emissions.add(it) }
        }

        repository.setAndReload(
            transactionWallets = listOf(walletOld, walletBuried),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = "needle",
        )
        advanceUntilIdle()

        val batch = requireNotNull(emissions.lastOrNull { it.searchCompleted }) {
            "expected a terminal search batch, got: " +
                emissions.map { it.records.map { r -> r.uid } to it.searchCompleted }
        }
        val uids = batch.records.map { it.uid }
        assertTrue("buried newer match must be present in the page: $uids", uids.contains("buried-match"))
        assertEquals("buried newer match must sort to the top of the page", "buried-match", uids.first())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_manySettledSources_doNotStarveBudgetForBuriedMatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val pageSource = createSource("account-page", token.blockchain)
        val buriedSource = createSource("account-deep", token.blockchain)
        val noiseSources = (0 until 9).map { createSource("account-noise-$it", token.blockchain) }

        // Source establishing the full page cutoff: 20 matches, all older than the buried one.
        val pageMatches = (0 until 20).map { i ->
            searchRecord(token, pageSource, "page-$i", 1_000L - i, "needle-address")
        }

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(pageSource) } returns simpleAdapter(pageMatches)
            every { getAdapter(buriedSource) } returns deepBuriedMatchAdapter(token, buriedSource)
            noiseSources.forEach { source ->
                every { getAdapter(source) } returns longNoiseAdapter(token, source)
            }
        }

        val repository = createRepository(adapterManager, testDispatcher, this)

        val emissions = mutableListOf<RecordsBatch>()
        val collectorJob = launch(testDispatcher) {
            repository.itemsFlow.collect { emissions.add(it) }
        }

        // Noise sources are listed before the buried source so they drain the shared scan budget first.
        val wallets = listOf(walletFor(token, pageSource)) +
            noiseSources.map { walletFor(token, it) } +
            walletFor(token, buriedSource)
        repository.setAndReload(
            transactionWallets = wallets,
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = "needle",
        )
        advanceUntilIdle()

        val batch = requireNotNull(emissions.lastOrNull { it.searchCompleted }) {
            "expected a terminal search batch"
        }
        val uids = batch.records.map { it.uid }
        assertTrue("deeply buried newer match must survive budget sharing: $uids", uids.contains("buried-deep-match"))
        assertEquals("buried newer match must sort to the top of the page", "buried-deep-match", uids.first())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_matchBeyond1000RecordsSingleSource_isFound() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val search = deepSearch(testDispatcher, noiseCount = 1500, withMatch = true)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("needle")

        assertEquals(listOf("deep-match"), batches.last().records.map { it.uid })

        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun searchQuery_matchBeyondPageScanBudget_foundAfterLoadNext() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val search = deepSearch(testDispatcher, noiseCount = 6000, withMatch = true)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("needle")

        val firstPage = batches.last()
        assertTrue(firstPage.searchCompleted)
        assertTrue(firstPage.records.isEmpty())
        assertFalse(firstPage.searchExhausted)

        search.repository.loadNext()
        advanceUntilIdle()

        assertEquals(listOf("deep-match"), batches.last().records.map { it.uid })
        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun searchQuery_absentQuery_pagesUntilHistoryExhausted() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val search = deepSearch(testDispatcher, noiseCount = 7000, withMatch = false)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("needle")
        search.pageUntilExhausted(batches)

        assertTrue(batches.last().searchExhausted)
        assertTrue(batches.last().records.isEmpty())
        assertEquals(search.records.map { it.uid }, search.served)

        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun searchQuery_closeAndReopenDeepQuery_stillFindsOtherSourceMatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val noiseSource = createSource("account-noise", searchToken.blockchain)
        val noiseWallet = walletFor(searchToken, noiseSource)
        val matchWallet = walletFor(searchToken, searchSource)
        val noise = (0 until 25_000).map {
            searchRecord(searchToken, noiseSource, "noise-$it", 2_000_000L - it, "plain")
        }
        val match = searchRecord(searchToken, searchSource, "match", 1_000L, "needle-address")
        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(noiseSource) } returns pagedAdapter(noise)
            every { getAdapter(searchSource) } returns pagedAdapter(listOf(match))
        }
        val repository = createRepository(adapterManager, testDispatcher, this)
        val (batches, collectorJob) = collectBatches(repository, testDispatcher)
        val wallets = listOf(noiseWallet, matchWallet)
        val search = { query: String? ->
            repository.setAndReload(wallets, null, FilterTransactionType.All, null, null, query)
            advanceUntilIdle()
        }

        search("needle")
        repeat(2) { repository.loadNext(); advanceUntilIdle() }
        assertEquals(15_000, batches.last().searchScannedCount)
        search(null)
        search("needle")

        val reopened = batches.last { it.searchCompleted }
        assertEquals(listOf("match"), reopened.records.map { it.uid })
        assertEquals(5_000, reopened.searchScannedCount)
        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun reload_activeSearch_startsNewSearchSession() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val search = deepSearch(testDispatcher, noiseCount = 25_000, withMatch = false)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("needle")
        search.repository.loadNext()
        advanceUntilIdle()
        val pagedSession = batches.last().searchSessionId
        search.repository.reload()
        advanceUntilIdle()

        assertEquals(10_000, batches[batches.size - 2].searchScannedCount)
        assertEquals(pagedSession, batches[batches.size - 2].searchSessionId)
        assertEquals(5_000, batches.last().searchScannedCount)
        assertTrue(batches.last().searchSessionId != pagedSession)
        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun searchQuery_absentQuery_reportsScannedCountPerPage() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val search = deepSearch(testDispatcher, noiseCount = 25_000, withMatch = false)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("needle")
        search.pageUntilExhausted(batches)

        val scannedCounts = batches.filter { it.searchCompleted }.map { it.searchScannedCount }
        assertEquals(listOf(5_000, 10_000, 15_000, 20_000, 25_000), scannedCounts.distinct())
        assertTrue(batches.last().searchExhausted)
        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun loadNext_firstPageFilledEarly_readsOneBudgetBeyondReachedDepth() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val matches = (0 until 20).map {
            searchRecord(searchToken, searchSource, "match-$it", 2_000_000L - it, "needle-address")
        }
        val noise = (0 until 12_000).map { fixtureRecord("noise-$it", 1_000_000L - it) }
        val search = searchFixture(testDispatcher, matches + noise)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("needle")
        assertEquals(100, batches.last().searchScannedCount)
        search.repository.loadNext()
        advanceUntilIdle()

        assertEquals(5_100, batches.last().searchScannedCount)
        assertTrue(search.served.size <= 5_100)
        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun handleUpdates_pausedSourceCachedAndOtherSourceGetsMatch_publishesMatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val otherSource = createSource("account-2", searchToken.blockchain)
        val noise = (0 until 6_000).map { fixtureRecord("noise-$it", 1_000_000L - it) }
        val otherRecords = mutableListOf<TransactionRecord>()
        val otherUpdates = MutableSharedFlow<List<TransactionRecord>>(extraBufferCapacity = 1)
        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(searchSource) } returns pagedAdapter(noise)
            every { getAdapter(otherSource) } returns pagedAdapter(otherRecords, updates = otherUpdates)
        }
        val repository = createRepository(adapterManager, testDispatcher, this)
        val (batches, collectorJob) = collectBatches(repository, testDispatcher)
        val wallets = listOf(walletFor(searchToken, searchSource), walletFor(searchToken, otherSource))
        repository.setAndReload(wallets, null, FilterTransactionType.All, null, null, "needle")
        advanceUntilIdle()
        assertEquals(5_000, batches.last().searchScannedCount)

        otherRecords += searchRecord(searchToken, otherSource, "new-match", 2_000_000L, "needle-address")
        otherUpdates.emit(otherRecords.toList())
        advanceUntilIdle()

        assertEquals(listOf("new-match"), batches.last().records.map { it.uid })
        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun handleUpdates_pausedSearchUnrelatedUpdate_doesNotScanDeeper() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val refresh = refreshFixture(testDispatcher, noise(7_000))

        refresh.start(pages = 1)
        assertEquals(5_000, refresh.batches.last().searchScannedCount)
        refresh.emitUpdate()

        assertEquals(5_000, refresh.batches.last().searchScannedCount)
        assertEquals(5_000, refresh.served.toSet().size)
        refresh.close()
    }

    @Test
    fun handleUpdates_invalidatedDeepCursor_restoresReachedDepthOnly() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val refresh = refreshFixture(testDispatcher, noise(12_000))

        refresh.start(pages = 2)
        assertEquals(10_000, refresh.batches.last().searchScannedCount)
        refresh.emitUpdate()

        assertEquals(10_000, refresh.batches.last().searchScannedCount)
        assertEquals(10_000, refresh.served.toSet().size)
        refresh.close()
    }

    @Test
    fun handleUpdates_exhaustedSourceGrows_readsOneBudgetOfGrowth() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val records = mutableListOf<TransactionRecord>()
        val refresh = refreshFixture(testDispatcher, records)

        refresh.start(pages = 1)
        assertTrue(refresh.batches.last().searchExhausted)
        records += noise(12_000)
        refresh.emitUpdate()

        assertEquals(5_000, refresh.batches.last().searchScannedCount)
        assertEquals(5_000, refresh.served.toSet().size)
        assertFalse(refresh.batches.last().searchExhausted)
        refresh.close()
    }

    @Test
    fun searchQuery_slowSource_stopsStepOnTimeLimit() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val adapter = pagedAdapter(noise(20_000), onRead = { delay(100) })
        val repository = createRepository(adapterManager(searchSource, adapter), testDispatcher, this)
        val (batches, collectorJob) = collectBatches(repository, testDispatcher)

        repository.setAndReload(
            listOf(walletFor(searchToken, searchSource)), null, FilterTransactionType.All, null, null, "needle",
        )
        advanceUntilIdle()

        val batch = batches.last()
        assertTrue(batch.searchTimedOut)
        assertFalse(batch.searchExhausted)
        assertTrue(batch.searchScannedCount in 1 until 5_000)
        assertTrue(currentTime <= 3_500)
        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_firstSourceHangs_matchInOtherSourceFound() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val otherSource = createSource("account-2", searchToken.blockchain)
        val match = searchRecord(searchToken, otherSource, "match", 2_000_000L, "needle-address")
        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(searchSource) } returns pagedAdapter(noise(100), onRead = { delay(10_000) })
            every { getAdapter(otherSource) } returns pagedAdapter(listOf(match))
        }
        val repository = createRepository(adapterManager, testDispatcher, this)
        val (batches, collectorJob) = collectBatches(repository, testDispatcher)
        val wallets = listOf(walletFor(searchToken, searchSource), walletFor(searchToken, otherSource))

        repository.setAndReload(wallets, null, FilterTransactionType.All, null, null, "needle")
        advanceUntilIdle()

        assertEquals(listOf("match"), batches.last().records.map { it.uid })
        assertTrue(batches.last().searchTimedOut)
        assertFalse(batches.last().searchExhausted)
        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_walletUpdatesFasterThanScanStep_stepStillCompletes() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val served = mutableListOf<String>()
        val updates = MutableSharedFlow<List<TransactionRecord>>(extraBufferCapacity = 64)
        // A full step needs 500 ms of reads while the busy address updates every 50 ms.
        val adapter = pagedAdapter(noise(7_000), served, updates, onRead = { delay(10) })
        val repository = createRepository(adapterManager(searchSource, adapter), testDispatcher, this)
        val (batches, collectorJob) = collectBatches(repository, testDispatcher)
        val ticker = backgroundScope.launch(testDispatcher) {
            while (true) {
                delay(50)
                updates.tryEmit(emptyList())
            }
        }

        repository.setAndReload(
            listOf(walletFor(searchToken, searchSource)), null, FilterTransactionType.All, null, null, "needle",
        )
        advanceTimeBy(2_000)
        ticker.cancel()

        assertEquals(5_000, batches.last { it.searchCompleted }.searchScannedCount)
        assertEquals(1, served.count { it == "noise-4000" })
        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun searchQuery_longHistoryAbsentQuery_streamsEachRecordOnceWithoutDisplayCache() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        val matcher = spyk(testSearchMatcher())
        val search = deepSearch(testDispatcher, noiseCount = 20_000, withMatch = false, matcher = matcher)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("needle")
        search.pageUntilExhausted(batches)

        assertTrue(batches.last().searchExhausted)
        assertEquals(search.records.map { it.uid }, search.served)
        verify(exactly = 20_000) { matcher.match(any(), any()) }

        // A display cache filled by the search would answer the regular page without reading.
        search.start(query = null)
        assertEquals(search.records.take(20).map { it.uid }, search.served.drop(20_000))

        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun searchQuery_swapsTabProviderSwapsBehindPlainSends_pagesOnlyEligibleRecords() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()
        val plainSends = (0 until 100).map { fixtureRecord("plain-$it", 10_000L - it) }
        val linkedSends = (0 until 25).map { fixtureRecord("linked-$it", 5_000L - it) }
        val storage = providerStorage(linkedSends.map { it.uid }.toSet())
        val search = searchFixture(testDispatcher, plainSends + linkedSends, storage)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("Отправлено", FilterTransactionType.Swap)

        assertEquals(linkedSends.take(20).map { it.uid }, batches.last().records.map { it.uid })
        assertFalse(batches.last().searchExhausted)

        search.repository.loadNext()
        advanceUntilIdle()

        assertEquals(linkedSends.map { it.uid }, batches.last().records.map { it.uid })
        assertTrue(batches.last().searchExhausted)
        (plainSends + linkedSends).forEach { verify(exactly = 1) { storage.getByOutgoingRecordUid(it.uid) } }
        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun searchQuery_swapsTabLongHistoryWithoutProviderSwaps_exhaustsNotFound() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()
        val sends = (0 until 20_000).map { fixtureRecord("send-$it", 1_000_000L - it) }
        val search = searchFixture(testDispatcher, sends, providerStorage(emptySet()))
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("Отправлено", FilterTransactionType.Swap)
        search.pageUntilExhausted(batches)

        assertTrue(batches.last().searchExhausted)
        assertTrue(batches.last().records.isEmpty())
        assertEquals(sends.map { it.uid }, search.served)
        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun searchQuery_allTabIncomingAllSpam_exhaustsNotFound() = runTest {
        assertAllSpamIncomingNotFound(tokenScreen = false)
    }

    @Test
    fun searchQuery_tokenScreenIncomingAllSpam_exhaustsNotFound() = runTest {
        assertAllSpamIncomingNotFound(tokenScreen = true)
    }

    @Test
    fun searchQuery_sentTabTransferBehindProviderSwapRows_showsTransferOnFirstPage() = runTest {
        assertSentTabSkipsProviderSwapRows(tokenScreen = false)
    }

    @Test
    fun searchQuery_tokenScreenSentTabTransferBehindProviderSwapRows_showsTransferOnFirstPage() = runTest {
        assertSentTabSkipsProviderSwapRows(tokenScreen = true)
    }

    @Test
    fun searchQuery_providerTitleMatchOnSentTab_resolvesSwapOnceAndHidesRow() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()
        val storage = providerStorage(setOf("swap-row"))
        val search = searchFixture(testDispatcher, listOf(fixtureRecord("swap-row", 10_000L)), storage)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("обмен", FilterTransactionType.Outgoing)

        assertTrue(batches.last().searchExhausted)
        assertTrue(batches.last().records.isEmpty())
        verify(exactly = 1) { storage.getByOutgoingRecordUid("swap-row") }
        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun searchQuery_spamSettingToggledDuringSearch_restartsWithNewSetting() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()
        var hideSpam = true
        val spamManager = mockk<SpamManager> {
            every { shouldHide(any()) } answers { hideSpam }
            every { hideSuspiciousTx } answers { hideSpam }
        }
        val incoming = incomingRecords(6_000)
        val visibility = testSearchVisibility(spamManager = spamManager)
        val search = searchFixture(testDispatcher, incoming, visibility = visibility)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("Получено")
        assertTrue(batches.last().records.isEmpty())
        assertFalse(batches.last().searchExhausted)

        hideSpam = false
        search.repository.loadNext()
        advanceUntilIdle()

        assertEquals(incoming.take(40).map { it.uid }, batches.last().records.map { it.uid })
        assertEquals("the scan restarts from the newest record", "in-0", search.served[5_000])
        collectorJob.cancel()
        search.repository.clear()
    }

    private fun TestScope.assertAllSpamIncomingNotFound(tokenScreen: Boolean) {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()
        val spamManager = mockk<SpamManager> {
            every { shouldHide(any()) } returns true
            every { hideSuspiciousTx } returns true
        }
        val incoming = incomingRecords(20_000)
        val visibility = testSearchVisibility(spamManager = spamManager)
        val search = searchFixture(testDispatcher, incoming, visibility = visibility)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("Получено", tokenScreen = tokenScreen)
        search.pageUntilExhausted(batches)

        assertTrue(batches.last().searchExhausted)
        assertTrue(batches.last().records.isEmpty())
        assertEquals(incoming.map { it.uid }, search.served)
        collectorJob.cancel()
        search.repository.clear()
    }

    private fun TestScope.assertSentTabSkipsProviderSwapRows(tokenScreen: Boolean) {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()
        mockRussianTitles()
        val swapRows = (0 until 20).map { fixtureRecord("swap-$it", 10_000L - it) }
        val transfer = fixtureRecord("transfer", 5_000L)
        val storage = providerStorage(swapRows.map { it.uid }.toSet())
        val search = searchFixture(testDispatcher, swapRows + transfer, storage)
        val (batches, collectorJob) = collectBatches(search.repository, testDispatcher)

        search.start("отпр", FilterTransactionType.Outgoing, tokenScreen)

        assertEquals(listOf("transfer"), batches.last().records.map { it.uid })
        collectorJob.cancel()
        search.repository.clear()
    }

    @Test
    fun reload_invokesAdapterGetTransactionsAgainForEachAdapter() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val source1 = createSource("account-1", Blockchain(BlockchainType.Ethereum, "Ethereum", null))
        val source2 = createSource("account-1", Blockchain(BlockchainType.Bitcoin, "Bitcoin", null))
        val wallet1 = TransactionWallet(token = null, source = source1, badge = null)
        val wallet2 = TransactionWallet(token = null, source = source2, badge = null)

        val adapter1 = simpleAdapter(emptyList())
        val adapter2 = simpleAdapter(emptyList())

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(source1) } returns adapter1
            every { getAdapter(source2) } returns adapter2
        }

        val repository = createRepository(adapterManager, testDispatcher, this)

        repository.setAndReload(
            transactionWallets = listOf(wallet1, wallet2),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        repository.reload()
        advanceUntilIdle()

        coVerify(atLeast = 2) { adapter1.getTransactions(any(), any(), any(), any(), any()) }
        coVerify(atLeast = 2) { adapter2.getTransactions(any(), any(), any(), any(), any()) }

        repository.clear()
    }

    @Test
    fun setWithAdditionalWallet_existingAdapterReused() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val source1 = createSource("account-1", Blockchain(BlockchainType.Ethereum, "Ethereum", null))
        val source2 = createSource("account-1", Blockchain(BlockchainType.Bitcoin, "Bitcoin", null))
        val wallet1 = TransactionWallet(token = null, source = source1, badge = null)
        val wallet2 = TransactionWallet(token = null, source = source2, badge = null)

        val adapter1 = simpleAdapter(emptyList())
        val adapter2 = simpleAdapter(emptyList())

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(source1) } returns adapter1
            every { getAdapter(source2) } returns adapter2
        }

        val repository = createRepository(adapterManager, testDispatcher, this)

        repository.setAndReload(
            transactionWallets = listOf(wallet1),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        repository.setAndReload(
            transactionWallets = listOf(wallet1, wallet2),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        // set() builds two adapter maps internally (adaptersMap + extraSwapAdaptersMap),
        // so each newly created adapter triggers 2 getAdapter() calls on the first appearance.
        // Without reuse, the second set() would invoke getAdapter(source1) twice more (= 4 total).
        // With reuse the count stays at 2 — proof that previousAdapters.remove() reused the wrapper.
        verify(exactly = 2) { adapterManager.getAdapter(source1) }
        verify(exactly = 2) { adapterManager.getAdapter(source2) }

        repository.clear()
    }

    @Test
    fun setSameWalletsWithDifferentTransactionType_loadsTransactionsWithNewType() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val source = createSource("account-1", Blockchain(BlockchainType.Ethereum, "Ethereum", null))
        val wallet = TransactionWallet(token = null, source = source, badge = null)
        val adapter = simpleAdapter(emptyList())

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(source) } returns adapter
        }

        val repository = createRepository(adapterManager, testDispatcher, this)

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = null,
            transactionType = FilterTransactionType.Incoming,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        // Iterating adaptersMap to propagate the new transactionType must not throw
        // and must trigger a reload with the new filter applied.
        coVerify(atLeast = 1) {
            adapter.getTransactions(any(), any(), any(), FilterTransactionType.Incoming, any())
        }

        repository.clear()
    }

    @Test
    fun concurrentSetAndReload_doesNotThrowConcurrentModificationException() {
        startKoinForTests()

        val blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
        val sources = (0 until 20).map { createSource("account-$it", blockchain) }
        val wallets = sources.map { TransactionWallet(token = null, source = it, badge = null) }
        val adapters = sources.associateWith { simpleAdapter(emptyList()) }

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            sources.forEach { source ->
                every { getAdapter(source) } returns adapters.getValue(source)
            }
        }

        val dispatcher = Dispatchers.Default
        val dispatcherProvider = object : DispatcherProvider {
            override val io = dispatcher
            override val default = dispatcher
            override val main = dispatcher
            override val applicationScope = CoroutineScope(dispatcher)
        }

        val repository = TransactionRecordRepository(
            adapterManager = adapterManager,
            swapProviderTransactionsStorage = mockk(relaxed = true),
            pendingRepository = emptyPendingRepository(),
            pendingConverter = mockk(relaxed = true),
            pendingTransactionMatcher = PendingTransactionMatcher(),
            searchMatcher = testSearchMatcher(),
            searchVisibility = testSearchVisibility(),
            locallyCreatedTransactionRepository = relaxedLocallyCreatedRepository(),
            dispatcherProvider = dispatcherProvider
        )

        val failure = AtomicReference<Throwable?>(null)
        val iterations = 500

        runBlocking {
            val writers = launch(Dispatchers.Default) {
                repeat(iterations) { i ->
                    try {
                        val subset = wallets.shuffled().take((i % wallets.size) + 1)
                        repository.setAndReload(
                            transactionWallets = subset,
                            wallet = null,
                            transactionType = FilterTransactionType.All,
                            blockchain = null,
                            contact = null,
                            searchQuery = null,
                        )
                    } catch (e: ConcurrentModificationException) {
                        failure.compareAndSet(null, e)
                    }
                }
            }
            val readers = launch(Dispatchers.Default) {
                repeat(iterations) {
                    try {
                        repository.reload()
                    } catch (e: ConcurrentModificationException) {
                        failure.compareAndSet(null, e)
                    }
                }
            }
            joinAll(writers, readers)
        }

        assertNull(
            "Expected no ConcurrentModificationException, but got: ${failure.get()}",
            failure.get()
        )

        repository.clear()
    }

    @Test
    fun itemsFlow_loadCompletedBeforeSubscription_lateCollectorReceivesBatch() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val source = createSource("account-1", token.blockchain)
        val wallet = walletFor(token, source)
        val record = createBitcoinOutgoingRecord(
            token = token,
            source = source,
            uid = "record-cached",
            timestamp = 1_715_000_000L,
            amount = BigDecimal("-0.00000563"),
            toAddress = "some-address",
        )

        val repository = createRepository(adapterManager(source, simpleAdapter(listOf(record))), testDispatcher, this)

        // The page finishes loading before anyone subscribes - the screen's collector is started
        // by a separate coroutine and can lose that race on a warm local database.
        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = wallet,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)
        advanceUntilIdle()

        assertEquals(listOf(listOf("record-cached")), emissions.toList())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun reload_oneSourceThrows_stillEmitsHealthySourceRecords() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val throwingSource = createSource("account-1", Blockchain(BlockchainType.Ethereum, "Ethereum", null))
        val healthySource = createSource("account-1", token.blockchain)
        val throwingWallet = TransactionWallet(token = null, source = throwingSource, badge = null)
        val healthyWallet = walletFor(token, healthySource)
        val record = createBitcoinOutgoingRecord(
            token = token,
            source = healthySource,
            uid = "record-healthy",
            timestamp = 1_715_000_000L,
            amount = BigDecimal("-0.00000563"),
            toAddress = "some-address",
        )

        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(throwingSource) } returns throwingAdapter()
            every { getAdapter(healthySource) } returns simpleAdapter(listOf(record))
        }
        val repository = createRepository(adapterManager, testDispatcher, this)

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(throwingWallet, healthyWallet),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        assertEquals(listOf(listOf("record-healthy")), emissions.toList())

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun reload_adapterNeverReturns_emitsFailedBatchAfterTimeout() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val source = createSource("account-1", token.blockchain)
        val wallet = walletFor(token, source)

        val stalledAdapter = mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery { getTransactions(any(), any(), any(), any(), any()) } coAnswers {
                CompletableDeferred<List<TransactionRecord>>().await()
            }
            every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
            every { getTransactionUrl(any()) } returns ""
        }
        val repository = createRepository(adapterManager(source, stalledAdapter), testDispatcher, this)

        val (emissions, collectorJob) = collectBatches(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = wallet,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertEquals(emptyList<TransactionRecord>(), emissions.single().records)
        assertTrue(
            "an empty batch from an unanswered source must be marked as a failed read",
            emissions.single().loadFailed
        )

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun reload_adapterTimesOutWithRecordsFromAnotherSource_batchIsNotMarkedFailed() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val healthySource = createSource("account-1", token.blockchain)
        val stalledSource = createSource("account-1", Blockchain(BlockchainType.Ethereum, "Ethereum", null))
        val healthyWallet = walletFor(token, healthySource)
        val stalledWallet = TransactionWallet(token = null, source = stalledSource, badge = null)
        val record = createBitcoinOutgoingRecord(
            token = token,
            source = healthySource,
            uid = "record-healthy",
            timestamp = 1_715_000_000L,
            amount = BigDecimal("-0.00000563"),
            toAddress = "some-address",
        )

        val stalledAdapter = mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery { getTransactions(any(), any(), any(), any(), any()) } coAnswers {
                CompletableDeferred<List<TransactionRecord>>().await()
            }
            every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
            every { getTransactionUrl(any()) } returns ""
        }
        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(stalledSource) } returns stalledAdapter
            every { getAdapter(healthySource) } returns simpleAdapter(listOf(record))
        }
        val repository = createRepository(adapterManager, testDispatcher, this)

        val (emissions, collectorJob) = collectBatches(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(stalledWallet, healthyWallet),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        assertEquals(listOf("record-healthy"), emissions.single().records.map { it.uid })
        assertEquals(false, emissions.single().loadFailed)

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun reload_allAdaptersAnswerWithNoRecords_batchIsNotMarkedFailed() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val source = createSource("account-1", token.blockchain)
        val wallet = walletFor(token, source)
        val repository = createRepository(
            adapterManager(source, simpleAdapter(emptyList())),
            testDispatcher,
            this,
        )

        val (emissions, collectorJob) = collectBatches(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = wallet,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        assertEquals(emptyList<TransactionRecord>(), emissions.single().records)
        assertEquals(false, emissions.single().loadFailed)

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun reload_adapterMissingForSelectedWallet_batchIsMarkedFailed() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val source = createSource("account-1", token.blockchain)
        val wallet = walletFor(token, source)
        // The adapter is gone by the time the repository resolves it, so nothing is read at all.
        // A vacuously complete empty result must not be published as a trustworthy empty history.
        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(source) } returns null
        }
        val repository = createRepository(adapterManager, testDispatcher, this)

        val (emissions, collectorJob) = collectBatches(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(wallet),
            wallet = wallet,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        assertEquals(emptyList<TransactionRecord>(), emissions.single().records)
        assertTrue(
            "a load that resolved no adapter must be reported as a failed read",
            emissions.single().loadFailed
        )

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun set_adapterReadyAfterFirstSetWithSameWallets_loadsItsRecords() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val source1 = createSource("account-1", Blockchain(BlockchainType.Ethereum, "Ethereum", null))
        val source2 = createSource("account-1", Blockchain(BlockchainType.Monero, "Monero", null))
        val wallets = listOf(source1, source2).map { TransactionWallet(token = null, source = it, badge = null) }
        fun record(uid: String, recordSource: TransactionSource, time: Long) =
            mockk<TransactionRecord>(relaxed = true) {
                every { this@mockk.uid } returns uid
                every { source } returns recordSource
                every { timestamp } returns time
                every { spam } returns false
                every { compareTo(any()) } returns 0
            }

        val lateAdapter = AtomicReference<ITransactionsAdapter?>(null)
        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(source1) } returns simpleAdapter(listOf(record("record-1", source1, 1000L)))
            every { getAdapter(source2) } answers { lateAdapter.get() }
        }
        val repository = createRepository(adapterManager, testDispatcher, this)

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.setAndReload(wallets, null, FilterTransactionType.All, null, null, null)
        advanceUntilIdle()

        lateAdapter.set(simpleAdapter(listOf(record("record-2", source2, 2000L))))
        repository.setAndReload(wallets, null, FilterTransactionType.All, null, null, null)
        advanceUntilIdle()

        val lastBatch = emissions.last()
        assertTrue(
            "a source whose adapter became ready after the first set() must be loaded, " +
                "but the last batch was: $lastBatch",
            lastBatch.containsAll(listOf("record-2", "record-1"))
        )

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun reload_swapFilterExtraAdapterTimesOut_batchIsMarkedFailed() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val fixture = createSwapFilterFixture("swap-never-returns")
        val adapter = swapFilterAdapter(
            token = fixture.token,
            outgoingRecords = { CompletableDeferred<List<TransactionRecord>>().await() },
        )
        val repository = createRepository(
            adapterManager = adapterManager(fixture.source, adapter),
            dispatcher = testDispatcher,
            scope = this,
        )

        val (emissions, collectorJob) = collectBatches(repository, testDispatcher)

        repository.setSwapWallet(fixture.wallet)
        advanceUntilIdle()

        assertEquals(emptyList<TransactionRecord>(), emissions.single().records)
        assertTrue(
            "an unanswered extra swap source must mark the empty batch as a failed read",
            emissions.single().loadFailed
        )

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun loadNext_oneSourceTimedOut_pageIsNotTreatedAsFullyLoaded() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        startKoinForTests()

        val token = createToken()
        val healthySource = createSource("account-1", token.blockchain)
        val stalledSource = createSource("account-1", Blockchain(BlockchainType.Ethereum, "Ethereum", null))
        val healthyWallet = walletFor(token, healthySource)
        val stalledWallet = TransactionWallet(token = null, source = stalledSource, badge = null)
        val record = createBitcoinOutgoingRecord(
            token = token,
            source = healthySource,
            uid = "record-healthy",
            timestamp = 1_715_000_000L,
            amount = BigDecimal("-0.00000563"),
            toAddress = "some-address",
        )

        val stalledAdapter = mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery { getTransactions(any(), any(), any(), any(), any()) } coAnswers {
                CompletableDeferred<List<TransactionRecord>>().await()
            }
            every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
            every { getTransactionUrl(any()) } returns ""
        }
        val adapterManager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(healthySource) } returns simpleAdapter(listOf(record))
            every { getAdapter(stalledSource) } returns stalledAdapter
        }
        val repository = createRepository(adapterManager, testDispatcher, this)

        val (emissions, collectorJob) = collectRecordUids(repository, testDispatcher)

        repository.setAndReload(
            transactionWallets = listOf(healthyWallet, stalledWallet),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
        advanceUntilIdle()

        repository.loadNext()
        advanceUntilIdle()

        assertEquals(
            "The silent source made the page look short, so loadNext() was refused as fully loaded",
            2,
            emissions.size
        )

        collectorJob.cancel()
        repository.clear()
    }

    @Test
    fun set_nativeBeamAndGameBeam_keepsNativeWalletAndEvmGroupingSeparate() = runTest {
        startKoinForTests()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val nativeToken = Token(
            Coin("beam", "Beam", "BEAM"), Blockchain(BlockchainType.Beam, "Beam", null), TokenType.Native, 8,
        )
        val gameToken = Token(
            Coin("beam-2", "Beam", "BEAM"), Blockchain(BlockchainType.Ethereum, "Ethereum", null),
            TokenType.Eip20("game-token-contract"), 18,
        )
        val nativeSource = createSource("beam-account", nativeToken.blockchain)
        val gameSource = createSource("beam-account", gameToken.blockchain)
        val nativeWallet = walletFor(nativeToken, nativeSource)
        val gameWallet = walletFor(gameToken, gameSource)
        val nativeRecord = mockk<TransactionRecord>(relaxed = true) {
            every { uid } returns "native-beam-history"
            every { source } returns nativeSource
            every { token } returns nativeToken
        }
        val nativeAdapter = simpleAdapter(listOf(nativeRecord))
        val gameAdapter = simpleAdapter(emptyList())
        val manager = adapterManager(nativeSource, nativeAdapter)
        every { manager.getAdapter(gameSource) } returns gameAdapter
        val repository = createRepository(manager, dispatcher, this)
        val (emissions, collector) = collectRecordUids(repository, dispatcher)
        try {
            repository.setAndReload(
                listOf(nativeWallet, gameWallet), null, FilterTransactionType.All, null, null, null,
            )
            advanceUntilIdle()
            assertEquals(listOf("native-beam-history"), emissions.last())
            coVerify { nativeAdapter.getTransactions(null, nativeToken, any(), FilterTransactionType.All, null) }
            coVerify { gameAdapter.getTransactions(null, null, any(), FilterTransactionType.All, null) }
        } finally {
            collector.cancel()
            repository.clear()
        }
    }

    @Test
    fun loadItems_beamDeletedDuringRead_reloadsAuthoritativeEmptySnapshot() = runTest {
        assertBeamUpdateDuringRead(searchQuery = null, deleted = true)
    }

    @Test
    fun loadItems_beamStatusChangedDuringRead_reloadsSameUidRecord() = runTest {
        assertBeamUpdateDuringRead(searchQuery = null, deleted = false)
    }

    @Test
    fun search_beamDeletedDuringRead_reloadsAuthoritativeEmptySnapshot() = runTest {
        assertBeamUpdateDuringRead(searchQuery = "transaction-id", deleted = true)
    }

    @Test
    fun search_beamStatusChangedDuringRead_reloadsSameUidRecord() = runTest {
        assertBeamUpdateDuringRead(searchQuery = "transaction-id", deleted = false)
    }

    @Test
    fun cancelPendingLoads_beamUpdateDuringRead_doesNotRestartCanceledLoad() = runTest {
        assertBeamUpdateDuringRead(searchQuery = null, deleted = true, cancelBeforeRelease = true)
    }

    @Test
    fun cancelPendingLoads_beamUpdateDuringSearch_doesNotRestartCanceledLoad() = runTest {
        assertBeamUpdateDuringRead(searchQuery = "transaction-id", deleted = true, cancelBeforeRelease = true)
    }

    private class SuspendedBeamRead {
        val original = beamHistoryRecord()
        val updated = beamHistoryRecord(status = BeamTransactionStatus.Registering)
        val wallet = TransactionWallet(original.token, original.source, null)
        val updates = MutableSharedFlow<List<TransactionRecord>>()
        val releaseStaleRead = CompletableDeferred<Unit>()
        val releaseFreshRead = CompletableDeferred<Unit>()
        var snapshot: List<TransactionRecord> = listOf(original)
        var readCount = 0
            private set
        val adapter = mockk<ITransactionsAdapter>(relaxed = true) {
            every { getTransactionRecordsFlow(any(), any(), any()) } returns updates
            every { getTransactionsReloadSignalFlow() } returns emptyFlow()
            coEvery { getTransactions(any(), any(), any(), any(), any()) } coAnswers { readSnapshot() }
        }

        private suspend fun readSnapshot(): List<TransactionRecord> {
            val captured = snapshot
            when (++readCount) {
                1 -> releaseStaleRead.await()
                2 -> releaseFreshRead.await()
                else -> error("Unexpected repeated reload")
            }
            return captured
        }
    }

    private suspend fun TestScope.assertBeamUpdateDuringRead(
        searchQuery: String?,
        deleted: Boolean,
        cancelBeforeRelease: Boolean = false,
    ) {
        startKoinForTests()
        val scenario = SuspendedBeamRead()
        val manager = adapterManager(scenario.original.source, scenario.adapter)
        val repository = createRepository(manager, StandardTestDispatcher(testScheduler), this)
        val emissions = mutableListOf<RecordsBatch>()
        backgroundScope.launch { repository.itemsFlow.collect { emissions.add(it) } }
        try {
            repository.set(listOf(scenario.wallet), scenario.wallet, FilterTransactionType.All, null, null, searchQuery)
            runCurrent()
            repository.reloadItems()
            runCurrent()
            assertEquals(1, scenario.readCount)
            scenario.snapshot = if (deleted) emptyList() else listOf(scenario.updated)
            repeat(3) {
                scenario.updates.emit(scenario.snapshot)
                runCurrent()
            }
            assertEquals(1, scenario.readCount)
            assertBeamReadCompletion(repository, scenario, emissions, searchQuery, cancelBeforeRelease)
        } finally {
            repository.clear()
        }
    }

    private fun TestScope.assertBeamReadCompletion(
        repository: TransactionRecordRepository,
        scenario: SuspendedBeamRead,
        emissions: List<RecordsBatch>,
        searchQuery: String?,
        cancelBeforeRelease: Boolean,
    ) {
        if (cancelBeforeRelease) repository.cancelPendingLoads()
        scenario.releaseStaleRead.complete(Unit)
        runCurrent()
        assertTrue("The invalidated read must not publish a batch", emissions.isEmpty())
        if (cancelBeforeRelease) {
            assertEquals(1, scenario.readCount)
            return
        }
        assertEquals(2, scenario.readCount)
        scenario.releaseFreshRead.complete(Unit)
        runCurrent()
        // A search re-publishes its page once more for the update it already applied; no read repeats.
        assertTrue(emissions.isNotEmpty())
        emissions.forEach { batch ->
            if (scenario.snapshot.isEmpty()) {
                assertTrue(batch.records.isEmpty())
            } else {
                assertSame(scenario.updated, batch.records.single())
            }
            assertEquals(searchQuery != null, batch.searchCompleted)
        }
        assertEquals(2, scenario.readCount)
    }

    private fun startKoinForTests() {
        startKoin {
            modules(module {
                single { mockk<cash.p.terminal.core.managers.CoinManager>(relaxed = true) }
            })
        }
    }

    private fun simpleAdapter(records: List<TransactionRecord>) =
        mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery { getTransactions(any(), any(), any(), any(), any()) } returns records
            every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
            every { getTransactionUrl(any()) } returns ""
        }

    private fun throwingAdapter() =
        mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery {
                getTransactions(any(), any(), any(), any(), any())
            } throws RuntimeException("adapter failure")
            every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
            every { getTransactionUrl(any()) } returns ""
        }

    private fun pagedAdapter(
        records: List<TransactionRecord>,
        served: MutableList<String>? = null,
        updates: Flow<List<TransactionRecord>> = emptyFlow(),
        onRead: suspend () -> Unit = {},
    ) = mockk<ITransactionsAdapter>(relaxed = true) {
            coEvery { getTransactions(any(), any(), any(), any(), any()) } coAnswers {
                onRead()
                val from = invocation.args[0] as? TransactionRecord
                val limit = invocation.args[2] as Int
                val startIndex = from
                    ?.let { fromRecord -> records.indexOfFirst { it.uid == fromRecord.uid } + 1 }
                    ?: 0
                records.drop(startIndex.coerceAtLeast(0)).take(limit)
                    .also { page -> served?.addAll(page.map { it.uid }) }
            }
            // The Swaps tab reaches these records only through its extra All-type source.
            coEvery { getTransactions(any(), any(), any(), FilterTransactionType.Swap, any()) } returns emptyList()
            every { getTransactionRecordsFlow(any(), any(), any()) } returns updates
            every { getTransactionUrl(any()) } returns ""
        }

    private fun swapFilterAdapter(
        token: Token,
        outgoingRecords: suspend () -> List<TransactionRecord>,
        outgoingUpdates: Flow<List<TransactionRecord>> = emptyFlow(),
    ) = mockk<ITransactionsAdapter>(relaxed = true) {
        coEvery {
            getTransactions(any(), token, any(), FilterTransactionType.Swap, any())
        } returns emptyList()
        coEvery {
            getTransactions(any(), token, any(), FilterTransactionType.All, any())
        } coAnswers { outgoingRecords() }
        every {
            getTransactionRecordsFlow(any(), FilterTransactionType.Swap, any())
        } returns emptyFlow()
        every {
            getTransactionRecordsFlow(any(), FilterTransactionType.All, any())
        } returns outgoingUpdates
        every { getTransactionUrl(any()) } returns ""
    }

    private fun adapterManager(source: TransactionSource, adapter: ITransactionsAdapter) =
        mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(source) } returns adapter
        }

    private fun emptyPendingRepository() = mockk<PendingTransactionRepository>(relaxed = true) {
        every { getActivePendingFlow(any()) } returns emptyFlow()
        coEvery { getPendingForWallet(any()) } returns emptyList()
    }

    private fun createRepository(
        adapterManager: TransactionAdapterManager,
        dispatcher: TestDispatcher,
        scope: TestScope,
        swapProviderTransactionsStorage: SwapProviderTransactionsStorage = mockk(relaxed = true),
        searchMatcher: TransactionRecordSearchMatcher = testSearchMatcher(swapProviderTransactionsStorage),
        searchVisibility: TransactionSearchVisibility = testSearchVisibility(swapProviderTransactionsStorage),
    ) = TransactionRecordRepository(
        adapterManager = adapterManager,
        swapProviderTransactionsStorage = swapProviderTransactionsStorage,
        pendingRepository = emptyPendingRepository(),
        pendingConverter = mockk(relaxed = true),
        pendingTransactionMatcher = PendingTransactionMatcher(),
        searchMatcher = searchMatcher,
        searchVisibility = searchVisibility,
        locallyCreatedTransactionRepository = relaxedLocallyCreatedRepository(),
        dispatcherProvider = TestDispatcherProvider(dispatcher, scope)
    )

    private fun relaxedLocallyCreatedRepository() =
        mockk<LocallyCreatedTransactionRepository>(relaxed = true)

    private fun createToken(): Token {
        val coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC")
        val blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null)
        return Token(
            coin = coin,
            blockchain = blockchain,
            type = TokenType.Derived(TokenType.Derivation.Bip86),
            decimals = 8
        )
    }

    private data class SwapFilterFixture(
        val token: Token,
        val source: TransactionSource,
        val wallet: TransactionWallet,
        val record: TransactionRecord,
    )

    private fun createSwapFilterFixture(recordUid: String): SwapFilterFixture {
        val token = createToken()
        val source = createSource(accountId = "account-1", blockchain = token.blockchain)
        val wallet = TransactionWallet(token = token, source = source, badge = null)
        val record = createBitcoinOutgoingRecord(
            token = token,
            source = source,
            uid = recordUid,
            timestamp = 1_715_000_000,
            amount = BigDecimal("-0.00000563"),
            toAddress = "provider-deposit-address",
        )

        return SwapFilterFixture(token, source, wallet, record)
    }

    private fun <T> TestScope.collectEmissions(
        repository: TransactionRecordRepository,
        dispatcher: TestDispatcher,
        transform: (RecordsBatch) -> T,
    ): Pair<MutableList<T>, Job> {
        val emissions = mutableListOf<T>()
        val collectorJob = launch(dispatcher) {
            repository.itemsFlow.collect { batch -> emissions.add(transform(batch)) }
        }
        return emissions to collectorJob
    }

    private fun TestScope.collectRecordUids(
        repository: TransactionRecordRepository,
        dispatcher: TestDispatcher,
    ) = collectEmissions(repository, dispatcher) { batch -> batch.records.map { it.uid } }

    private fun TestScope.collectBatches(
        repository: TransactionRecordRepository,
        dispatcher: TestDispatcher,
    ) = collectEmissions(repository, dispatcher) { it }

    // Mirrors the production contract: TransactionsService calls set() and only reloads
    // when it reports a change. set() no longer triggers the reload itself.
    private class ProviderSearchFixture(
        val source: TransactionSource,
        val wallet: TransactionWallet,
        val record: TransactionRecord,
    ) {
        var storedSwap = testProviderSwap(TransactionStatusEnum.WAITING, record.uid)
        val updates = MutableSharedFlow<List<SwapProviderTransaction>>(extraBufferCapacity = 1)
        val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
            every { getAllAcrossAccounts() } answers { listOf(storedSwap) }
            every { getByOutgoingRecordUid(record.uid) } answers { storedSwap }
            every { getByTokenIn(any(), any(), any()) } returns null
            every { observeAll() } returns updates
        }
    }

    private fun providerSearchFixture(): ProviderSearchFixture {
        val token = createToken()
        val source = createSource(accountId = "account-1", blockchain = token.blockchain)
        val record = createBitcoinOutgoingRecord(
            token = token,
            source = source,
            uid = "provider-payment",
            timestamp = 1_715_000_000L,
            amount = BigDecimal("-0.001"),
            toAddress = "provider-deposit",
        )
        return ProviderSearchFixture(source, TransactionWallet(token = token, source = source, badge = null), record)
    }

    private fun TransactionRecordRepository.searchAllTab(wallet: TransactionWallet, searchQuery: String) =
        setAndReload(
            transactionWallets = listOf(wallet),
            wallet = null,
            transactionType = FilterTransactionType.All,
            blockchain = null,
            contact = null,
            searchQuery = searchQuery,
        )

    private fun TransactionRecordRepository.setAndReload(
        transactionWallets: List<TransactionWallet>,
        wallet: TransactionWallet?,
        transactionType: FilterTransactionType,
        blockchain: Blockchain?,
        contact: Contact?,
        searchQuery: String?,
    ) {
        val changed = set(transactionWallets, wallet, transactionType, blockchain, contact, searchQuery)
        if (changed) reloadItems()
    }

    private fun TransactionRecordRepository.setSwapWallet(wallet: TransactionWallet) {
        setAndReload(
            transactionWallets = listOf(wallet),
            wallet = null,
            transactionType = FilterTransactionType.Swap,
            blockchain = null,
            contact = null,
            searchQuery = null,
        )
    }

    private fun walletFor(token: Token, source: TransactionSource) =
        TransactionWallet(token = token, source = source, badge = null)

    private fun createSource(accountId: String, blockchain: Blockchain): TransactionSource {
        val account = Account(
            id = accountId,
            name = accountId,
            type = mockk(relaxed = true),
            origin = AccountOrigin.Created,
            level = 0
        )
        return TransactionSource(blockchain = blockchain, account = account, meta = null)
    }

    private fun createContact(uid: String, blockchain: Blockchain, address: String) = Contact(
        uid = uid,
        name = uid,
        addresses = listOf(ContactAddress(blockchain = blockchain, address = address))
    )

    // Source B for the buried-match scenario: 100 newer non-matching records hide a single match
    // (still newer than source A's matches) behind them, so this source's first scan batch
    // (searchBatchSize = 100) returns no matches and must not be finalized before it is reached.
    private fun buriedMatchAdapter(token: Token, source: TransactionSource): ITransactionsAdapter {
        val noise = (0 until 100).map { i ->
            searchRecord(token, source, "noise-$i", 2_000L - i, "plain-address")
        }
        val match = searchRecord(token, source, "buried-match", 1_900L, "needle-address")
        return pagedAdapter(noise + match)
    }

    private inner class SearchFixture(
        val repository: TransactionRecordRepository,
        val records: List<TransactionRecord>,
        val served: List<String>,
        private val scope: TestScope,
    ) {
        private val wallet = walletFor(searchToken, searchSource)

        // The TokenBalance screen selects its single wallet; the Transactions tab selects none.
        fun start(
            query: String?,
            tab: FilterTransactionType = FilterTransactionType.All,
            tokenScreen: Boolean = false,
        ) {
            repository.setAndReload(listOf(wallet), wallet.takeIf { tokenScreen }, tab, null, null, query)
            scope.advanceUntilIdle()
        }

        fun pageUntilExhausted(batches: List<RecordsBatch>) {
            repeat(MAX_TEST_PAGES) {
                if (batches.last().searchExhausted) return
                repository.loadNext()
                scope.advanceUntilIdle()
            }
        }
    }

    private fun TestScope.searchFixture(
        dispatcher: TestDispatcher,
        records: List<TransactionRecord>,
        storage: SwapProviderTransactionsStorage = mockk(relaxed = true),
        matcher: TransactionRecordSearchMatcher = testSearchMatcher(storage),
        visibility: TransactionSearchVisibility = testSearchVisibility(storage),
    ): SearchFixture {
        val served = mutableListOf<String>()
        val repository = createRepository(
            adapterManager(searchSource, pagedAdapter(records, served)),
            dispatcher,
            this,
            swapProviderTransactionsStorage = storage,
            searchMatcher = matcher,
            searchVisibility = visibility,
        )
        return SearchFixture(repository, records, served, this)
    }

    // One non-matching source that reports an update after the search reached its depth.
    private inner class RefreshFixture(
        val repository: TransactionRecordRepository,
        val served: List<String>,
        private val updates: MutableSharedFlow<List<TransactionRecord>>,
        private val dispatcher: TestDispatcher,
        private val scope: TestScope,
    ) {
        lateinit var batches: List<RecordsBatch>
        private var collectorJob: Job? = null

        fun start(pages: Int) {
            val (collected, job) = scope.collectBatches(repository, dispatcher)
            batches = collected
            collectorJob = job
            val wallets = listOf(walletFor(searchToken, searchSource))
            repository.setAndReload(wallets, null, FilterTransactionType.All, null, null, "needle")
            scope.advanceUntilIdle()
            repeat(pages - 1) { repository.loadNext(); scope.advanceUntilIdle() }
        }

        fun emitUpdate() {
            check(updates.tryEmit(emptyList()))
            scope.advanceUntilIdle()
        }

        fun close() {
            collectorJob?.cancel()
            repository.clear()
        }
    }

    private fun noise(count: Int) = (0 until count).map { fixtureRecord("noise-$it", 1_000_000L - it) }

    private fun TestScope.refreshFixture(dispatcher: TestDispatcher, records: List<TransactionRecord>): RefreshFixture {
        val served = mutableListOf<String>()
        val updates = MutableSharedFlow<List<TransactionRecord>>(extraBufferCapacity = 1)
        val adapter = pagedAdapter(records, served, updates)
        val repository = createRepository(adapterManager(searchSource, adapter), dispatcher, this)
        return RefreshFixture(repository, served, updates, dispatcher, this)
    }

    // noiseCount newer non-matching records, optionally followed by one match.
    private fun TestScope.deepSearch(
        dispatcher: TestDispatcher,
        noiseCount: Int,
        withMatch: Boolean,
        matcher: TransactionRecordSearchMatcher = testSearchMatcher(),
    ): SearchFixture {
        val noise = (0 until noiseCount).map { fixtureRecord("noise-$it", 1_000_000L - it) }
        val match = searchRecord(searchToken, searchSource, "deep-match", 1_000_000L - noiseCount, "needle-address")
        return searchFixture(dispatcher, if (withMatch) noise + match else noise, matcher = matcher)
    }

    private fun fixtureRecord(
        uid: String,
        timestamp: Long,
        type: TransactionRecordType = TransactionRecordType.BITCOIN_OUTGOING,
    ) = searchRecord(searchToken, searchSource, uid, timestamp, "plain", type)

    private fun incomingRecords(count: Int) =
        (0 until count).map { fixtureRecord("in-$it", 1_000_000L - it, TransactionRecordType.BITCOIN_INCOMING) }

    // Every given uid is the outgoing record of a finished provider swap.
    private fun providerStorage(linkedUids: Set<String>) = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
        every { getByOutgoingRecordUid(any()) } answers {
            firstArg<String>().takeIf { it in linkedUids }?.let { testProviderSwap(TransactionStatusEnum.FINISHED, it) }
        }
        every { getByTokenIn(any(), any(), any()) } returns null
        every { getAllAcrossAccounts() } returns linkedUids.map { testProviderSwap(TransactionStatusEnum.FINISHED, it) }
        every { observeAll() } returns emptyFlow()
    }

    private fun searchRecord(
        token: Token,
        source: TransactionSource,
        uid: String,
        timestamp: Long,
        toAddress: String,
        type: TransactionRecordType = TransactionRecordType.BITCOIN_OUTGOING,
    ) = createBitcoinOutgoingRecord(
        token = token,
        source = source,
        uid = uid,
        timestamp = timestamp,
        amount = BigDecimal("-0.00000563"),
        toAddress = toAddress,
        type = type,
    )

    // A long-history source whose records are all older than the page cutoff: it settles after the
    // first scan round (frontier below the cutoff) yet never finishes, so under the buggy code it
    // keeps consuming the shared scan budget every round and starves the source below.
    private fun longNoiseAdapter(token: Token, source: TransactionSource): ITransactionsAdapter {
        val records = (0 until 700).map { i ->
            searchRecord(token, source, "noise-${source.account.id}-$i", 900L - i, "plain-address")
        }
        return pagedAdapter(records)
    }

    // The only source with a real match, hidden at scan depth 650 behind records that are all newer
    // than the cutoff (so this source never settles). It can only be reached if settled sources stop
    // draining the shared budget; the match is also newer than the page records, so it must sort first.
    private fun deepBuriedMatchAdapter(token: Token, source: TransactionSource): ITransactionsAdapter {
        val noise = (0 until 649).map { i ->
            searchRecord(token, source, "deep-noise-$i", 3_000L - i, "plain-address")
        }
        val match = searchRecord(token, source, "buried-deep-match", 1_500L, "needle-address")
        return pagedAdapter(noise + match)
    }

    private fun createBitcoinOutgoingRecord(
        token: Token,
        source: TransactionSource,
        uid: String,
        timestamp: Long,
        amount: BigDecimal,
        toAddress: String,
        type: TransactionRecordType = TransactionRecordType.BITCOIN_OUTGOING,
    ) = BitcoinTransactionRecord(
        token = token,
        amount = amount,
        to = listOf(toAddress),
        from = null,
        changeAddresses = emptyList(),
        uid = uid,
        transactionHash = uid,
        transactionIndex = 0,
        blockHeight = null,
        confirmationsThreshold = 1,
        timestamp = timestamp,
        failed = false,
        memo = null,
        source = source,
        sentToSelf = false,
        transactionRecordType = type,
        fee = null,
        lockInfo = null,
        conflictingHash = null,
        showRawTransaction = true,
        replaceable = false,
    )

    private companion object {
        const val MAX_TEST_PAGES = 50
    }
}
