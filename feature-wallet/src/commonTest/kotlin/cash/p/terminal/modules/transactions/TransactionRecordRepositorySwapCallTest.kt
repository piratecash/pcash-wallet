package cash.p.terminal.modules.transactions

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.CoinManager
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.solanakit.models.Transaction as SolanaTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRecordRepositorySwapCallTest {

    private val blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
    private val account = Account("account-1", "account-1", mockk(relaxed = true), AccountOrigin.Created, 0)
    private val source = TransactionSource(blockchain, account, null)
    private val baseToken = token("ethereum", TokenType.Native)
    private val wallet = TransactionWallet(token = null, source = source, badge = null)
    private val noSwapProviders = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
        every { getByOutgoingRecordUid(any()) } returns null
        every { getByTokenIn(any(), any(), any()) } returns null
        every { observeAll() } returns emptyFlow()
    }
    private val rawReadDepths = mutableListOf<Int>()

    @Before
    fun setUp() {
        startKoin { modules(module { single { mockk<CoinManager>(relaxed = true) } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun token(uid: String, type: TokenType) = Token(Coin(uid, uid, uid.uppercase()), blockchain, type, 18)

    private fun record(
        index: Int,
        type: TransactionRecordType = TransactionRecordType.EVM_INCOMING,
        flagged: Boolean = false,
        hashPrefix: Byte = 0,
    ): EvmTransactionRecord {
        val hash = ByteArray(32) { hashPrefix }
        BigInteger.valueOf(index.toLong()).toByteArray().let { it.copyInto(hash, 32 - it.size) }
        val transaction = Transaction(
            hash = hash,
            timestamp = 1_700_000_000L - index,
            isFailed = false,
            from = Address("0x1111111111111111111111111111111111111111"),
            to = Address("0x2222222222222222222222222222222222222222"),
            value = BigInteger.ZERO,
        )
        return EvmTransactionRecord(
            transaction = transaction,
            token = baseToken,
            source = source,
            protected = false,
            transactionRecordType = type,
            swapDecodedFromCall = flagged,
        )
    }

    private fun flaggedSwap(index: Int) =
        record(index, TransactionRecordType.EVM_UNKNOWN_SWAP, flagged = true, hashPrefix = 0xAB.toByte())

    private fun fillers(count: Int, hashPrefix: Byte = 0) = List(count) { record(it, hashPrefix = hashPrefix) }

    private fun fillersWith(vararg swaps: Pair<Int, EvmTransactionRecord>, total: Int): List<TransactionRecord> {
        val byIndex = swaps.toMap()
        return List(total) { byIndex[it] ?: record(it) }
    }

    private fun adapter(
        allRecords: List<TransactionRecord>,
        taggedSwaps: List<TransactionRecord> = emptyList(),
        token: Token? = null,
        onRead: suspend (Int) -> Unit = {},
    ) = mockk<ITransactionsAdapter>(relaxed = true) {
        coEvery { getTransactions(any(), any(), any(), FilterTransactionType.All, any()) } returns emptyList()
        coEvery {
            getTransactions(any(), token, any(), FilterTransactionType.All, any())
        } coAnswers {
            val from = invocation.args[0] as? TransactionRecord
            val limit = invocation.args[2] as Int
            val start = from?.let { allRecords.indexOf(it) + 1 } ?: 0
            rawReadDepths += start + limit
            onRead(rawReadDepths.size)
            allRecords.drop(start).take(limit)
        }
        coEvery {
            getTransactions(any(), any(), any(), FilterTransactionType.Swap, any())
        } returns taggedSwaps
        every { getTransactionRecordsFlow(any(), any(), any()) } returns emptyFlow()
        every { getTransactionsReloadSignalFlow() } returns emptyFlow()
        every { getTransactionUrl(any()) } returns ""
    }

    private fun TestScope.repository(
        adapter: ITransactionsAdapter,
        otherAdapters: Map<TransactionSource, ITransactionsAdapter> = emptyMap(),
    ): Pair<TransactionRecordRepository, MutableList<RecordsBatch>> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val manager = mockk<TransactionAdapterManager>(relaxed = true) {
            every { getAdapter(source) } returns adapter
            otherAdapters.forEach { (otherSource, other) -> every { getAdapter(otherSource) } returns other }
        }
        val pendingRepository = mockk<PendingTransactionRepository>(relaxed = true) {
            every { getActivePendingFlow(any()) } returns emptyFlow()
            coEvery { getPendingForWallet(any()) } returns emptyList()
        }
        val repository = TransactionRecordRepository(
            adapterManager = manager,
            swapProviderTransactionsStorage = noSwapProviders,
            pendingRepository = pendingRepository,
            pendingConverter = mockk(relaxed = true),
            pendingTransactionMatcher = PendingTransactionMatcher(),
            locallyCreatedTransactionRepository = mockk(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(dispatcher, this),
        )
        val batches = mutableListOf<RecordsBatch>()
        backgroundScope.launch(dispatcher) { repository.itemsFlow.collect { batches += it } }
        return repository to batches
    }

    private fun TransactionRecordRepository.openSwapTab(
        selected: TransactionWallet? = null,
        searchQuery: String? = null,
    ) {
        val wallets = listOf(selected ?: wallet)
        if (set(wallets, selected, FilterTransactionType.Swap, null, null, searchQuery)) reloadItems()
    }

    private fun TransactionRecordRepository.searchAllTab(wallets: List<TransactionWallet> = listOf(wallet)) {
        set(wallets, null, FilterTransactionType.All, null, null, "abababab")
        reloadItems()
    }

    private fun assertBatches(batches: List<RecordsBatch>, vararg expected: List<TransactionRecord>) {
        assertEquals(expected.map { list -> list.map { it.uid } }, batches.map { batch -> batch.records.map { it.uid } })
    }

    private fun assertFlaggedSwapVisibleUnderCoin(coin: Token) = runTest(timeout = 30.seconds) {
        val swap = flaggedSwap(0)
        val adapter = adapter(listOf(swap), token = coin)
        val (repository, batches) = repository(adapter)

        repository.openSwapTab(TransactionWallet(coin, source, null))
        advanceUntilIdle()

        assertBatches(batches, listOf(swap))
        coVerify(exactly = 0) { adapter.getTransactions(any(), any(), any(), FilterTransactionType.Outgoing, any()) }
        repository.clear()
    }

    @Test
    fun swapTab_flaggedSwap_appearsOnce() = runTest(timeout = 30.seconds) {
        val swap = flaggedSwap(0)
        val (repository, batches) = repository(adapter(listOf(swap)))

        repository.openSwapTab()
        advanceUntilIdle()

        assertBatches(batches, listOf(swap))
        repository.clear()
    }

    @Test
    fun swapTab_underOutCoin_showsFlaggedSwap() =
        assertFlaggedSwapVisibleUnderCoin(token("token-out", TokenType.Eip20("0x" + "a".repeat(40))))

    @Test
    fun swapTab_underInCoin_showsFlaggedSwap() =
        assertFlaggedSwapVisibleUnderCoin(token("token-in", TokenType.Eip20("0x" + "b".repeat(40))))

    @Test
    fun swapTab_taggedSwapAlsoInExtraSource_isNotDuplicated() = runTest(timeout = 30.seconds) {
        val tagged = record(0, TransactionRecordType.EVM_SWAP)
        val (repository, batches) = repository(adapter(listOf(tagged), taggedSwaps = listOf(tagged)))

        repository.openSwapTab()
        advanceUntilIdle()

        assertBatches(batches, listOf(tagged))
        repository.clear()
    }

    @Test
    fun swapTab_flaggedSwapBeyondFirstWindow_appearsWithoutEmptyEmission() = runTest(timeout = 30.seconds) {
        val swap = flaggedSwap(30)
        val (repository, batches) = repository(adapter(fillersWith(30 to swap, total = 100)))

        repository.openSwapTab()
        advanceUntilIdle()

        assertBatches(batches, listOf(swap))
        assertEquals(40, rawReadDepths.max())
        repository.clear()
    }

    @Test
    fun swapTab_noMatchUntilSourceExhausted_stopsAndEmitsEmptyOnce() = runTest(timeout = 30.seconds) {
        val (repository, batches) = repository(adapter(fillers(30)))

        repository.openSwapTab()
        advanceUntilIdle()

        assertBatches(batches, emptyList())
        assertEquals(false, batches.single().loadFailed)
        assertEquals(40, rawReadDepths.max())
        repository.clear()
    }

    @Test
    fun swapTab_noMatchInLongHistory_stopsAtScanBudget() = runTest(timeout = 30.seconds) {
        val (repository, batches) = repository(adapter(fillers(6000)))

        repository.openSwapTab()
        advanceUntilIdle()

        assertBatches(batches, emptyList())
        assertEquals(2560, rawReadDepths.max())
        repository.clear()
    }

    @Test
    fun loadNext_afterSkippedPages_continuesAndKeepsShownSwaps() = runTest(timeout = 30.seconds) {
        val first = flaggedSwap(25)
        val second = flaggedSwap(45)
        val (repository, batches) = repository(adapter(fillersWith(25 to first, 45 to second, total = 100)))

        repository.openSwapTab()
        advanceUntilIdle()
        repository.loadNext()
        advanceUntilIdle()

        assertBatches(batches, listOf(first), listOf(first, second))
        repository.clear()
    }

    @Test
    fun loadNext_nextWindowAddsNoSwap_widensToOlderSwap() = runTest(timeout = 30.seconds) {
        val first = flaggedSwap(5)
        val second = flaggedSwap(65)
        val (repository, batches) = repository(adapter(fillersWith(5 to first, 65 to second, total = 200)))

        repository.openSwapTab()
        advanceUntilIdle()
        repository.loadNext()
        advanceUntilIdle()

        assertBatches(batches, listOf(first), listOf(first, second))
        assertEquals(80, rawReadDepths.max())
        repository.clear()
    }

    @Test
    fun loadNext_nextWindowOnlyRepeatsTaggedSwap_widensToOlderSwap() = runTest(timeout = 30.seconds) {
        val tagged = flaggedSwap(25)
        val older = flaggedSwap(65)
        val (repository, batches) = repository(
            adapter(fillersWith(25 to tagged, 65 to older, total = 200), taggedSwaps = listOf(tagged))
        )

        repository.openSwapTab()
        advanceUntilIdle()
        repository.loadNext()
        advanceUntilIdle()

        assertEquals(setOf(tagged.uid, older.uid), batches.last().records.map { it.uid }.toSet())
        repository.clear()
    }

    @Test
    fun loadNext_updateDuringWidening_keepsOlderSwap() = runTest(timeout = 30.seconds) {
        val first = flaggedSwap(5)
        val second = flaggedSwap(65)
        val updates = MutableSharedFlow<List<SwapProviderTransaction>>(extraBufferCapacity = 1)
        every { noSwapProviders.observeAll() } returns updates
        var updateSent = false
        val adapter = adapter(fillersWith(5 to first, 65 to second, total = 200)) {
            if (!updateSent && rawReadDepths.last() > 40) {
                updateSent = true
                updates.tryEmit(emptyList())
                delay(1)
            }
        }
        val (repository, batches) = repository(adapter)

        repository.openSwapTab()
        advanceUntilIdle()
        repository.loadNext()
        advanceUntilIdle()

        assertTrue("update was not sent during widening", updateSent)
        assertEquals(3, batches.size)
        assertEquals(setOf(first.uid, second.uid), batches.last().records.map { it.uid }.toSet())
        repository.clear()
    }

    @Test
    fun recordsUpdate_sameWindowUnchanged_doesNotScanDeeper() = runTest(timeout = 30.seconds) {
        val updates = MutableSharedFlow<Unit>()
        val adapter = adapter(fillersWith(5 to flaggedSwap(5), total = 200))
        every { adapter.getTransactionsReloadSignalFlow() } returns updates
        val (repository, _) = repository(adapter)

        repository.openSwapTab()
        advanceUntilIdle()
        val readsBeforeUpdate = rawReadDepths.size
        updates.emit(Unit)
        advanceUntilIdle()

        assertTrue("update did not re-read the page", rawReadDepths.size > readsBeforeUpdate)
        assertEquals(20, rawReadDepths.max())
        repository.clear()
    }

    @Test
    fun swapTab_nonEmptyFirstPage_doesNotScanDeeper() = runTest(timeout = 30.seconds) {
        val swap = flaggedSwap(5)
        val (repository, batches) = repository(adapter(fillersWith(5 to swap, total = 100)))

        repository.openSwapTab()
        advanceUntilIdle()

        assertBatches(batches, listOf(swap))
        assertEquals(20, rawReadDepths.max())
        repository.clear()
    }

    @Test
    fun swapTab_incompleteRead_doesNotScanDeeper() = runTest(timeout = 30.seconds) {
        val adapter = adapter(fillers(100))
        coEvery {
            adapter.getTransactions(any(), any(), any(), FilterTransactionType.Swap, any())
        } throws RuntimeException("source failure")
        val (repository, batches) = repository(adapter)

        repository.openSwapTab()
        advanceUntilIdle()

        assertTrue(batches.single().loadFailed)
        assertEquals(20, rawReadDepths.max())
        repository.clear()
    }

    @Test
    fun swapTab_filterChangedDuringDeeperRead_publishesNothing() = runTest(timeout = 30.seconds) {
        lateinit var repository: TransactionRecordRepository
        val swap = flaggedSwap(30)
        val adapter = adapter(fillersWith(30 to swap, total = 100)) { readNumber ->
            if (readNumber == 2) repository.set(listOf(wallet), null, FilterTransactionType.All, null, null, null)
        }
        val (created, batches) = repository(adapter)
        repository = created

        repository.openSwapTab()
        advanceUntilIdle()

        assertTrue("stale page was published: ${batches.size}", batches.isEmpty())
        repository.clear()
    }

    @Test
    fun swapTab_solanaIncomingMatchingProviderByAmount_isNotShown() = runTest(timeout = 30.seconds) {
        every { noSwapProviders.getByTokenIn(any(), any(), any()) } returns mockk(relaxed = true)
        val transaction = mockk<SolanaTransaction>(relaxed = true) {
            every { hash } returns "solana-incoming"
            every { timestamp } returns 1_700_000_000L
            every { pending } returns false
            every { error } returns null
            every { fee } returns null
        }
        val incoming = SolanaTransactionRecord(
            token = baseToken,
            source = source,
            transactionRecordType = TransactionRecordType.SOLANA_INCOMING,
            transaction = transaction,
            mainValue = TransactionValue.CoinValue(baseToken, BigDecimal.ONE),
        )
        val (repository, batches) = repository(adapter(listOf(incoming)))

        repository.openSwapTab()
        advanceUntilIdle()

        assertBatches(batches, emptyList())
        repository.clear()
    }

    @Test
    fun search_swapTab_flaggedSwapBehindFullPageOfOrdinaryMatches_isFound() = runTest(timeout = 30.seconds) {
        val swap = flaggedSwap(20)
        val records = fillers(20, hashPrefix = 0xAB.toByte()) + swap
        val (repository, batches) = repository(adapter(records))

        repository.openSwapTab(searchQuery = "abababab")
        advanceUntilIdle()

        assertEquals(listOf(swap.uid), batches.last().records.map { it.uid })
        repository.clear()
    }

    @Test
    fun search_swapTab_moreFlaggedSwapsThanPage_loadNextReachesAll() = runTest(timeout = 30.seconds) {
        val swaps = List(21, ::flaggedSwap)
        val (repository, batches) = repository(adapter(swaps))

        repository.openSwapTab(searchQuery = "abababab")
        advanceUntilIdle()

        assertEquals(20, batches.last().records.size)
        assertFalse(batches.last().searchExhausted)

        repository.loadNext()
        advanceUntilIdle()

        assertEquals(swaps.map { it.uid }.toSet(), batches.last().records.map { it.uid }.toSet())
        assertTrue(batches.last().searchExhausted)
        repository.clear()
    }

    @Test
    fun search_twoNormalSourcesExceedingPage_loadNextReachesAll() = runTest(timeout = 30.seconds) {
        val prefix = 0xAB.toByte()
        val firstMatches = List(15) { record(it, hashPrefix = prefix) }
        val secondMatches = List(15) { record(100 + it, hashPrefix = prefix) }
        val otherSource = TransactionSource(Blockchain(BlockchainType.BinanceSmartChain, "BSC", null), account, null)
        val otherWallet = TransactionWallet(token = null, source = otherSource, badge = null)
        val (repository, batches) = repository(
            adapter(firstMatches),
            mapOf(otherSource to adapter(secondMatches)),
        )

        repository.searchAllTab(listOf(wallet, otherWallet))
        advanceUntilIdle()

        assertEquals(20, batches.last().records.size)
        assertFalse(batches.last().searchExhausted)

        repository.loadNext()
        advanceUntilIdle()

        assertEquals((firstMatches + secondMatches).map { it.uid }.toSet(), batches.last().records.map { it.uid }.toSet())
        assertTrue(batches.last().searchExhausted)
        repository.clear()
    }

    @Test
    fun search_singleSourceMoreMatchesThanPageInShortHistory_loadNextReachesAll() = runTest(timeout = 30.seconds) {
        val matches = fillers(25, hashPrefix = 0xAB.toByte())
        val (repository, batches) = repository(adapter(matches + List(15) { record(25 + it) }))

        repository.searchAllTab()
        advanceUntilIdle()

        assertEquals(20, batches.last().records.size)
        assertFalse(batches.last().searchExhausted)

        repository.loadNext()
        advanceUntilIdle()

        assertEquals(matches.map { it.uid }.toSet(), batches.last().records.map { it.uid }.toSet())
        assertTrue(batches.last().searchExhausted)
        repository.clear()
    }

    @Test
    fun search_fewerMatchesThanPage_isExhaustedWithoutFurtherLoad() = runTest(timeout = 30.seconds) {
        val matches = fillers(5, hashPrefix = 0xAB.toByte())
        val (repository, batches) = repository(adapter(matches + List(15) { record(5 + it) }))

        repository.searchAllTab()
        advanceUntilIdle()

        assertEquals(matches.map { it.uid }, batches.single().records.map { it.uid })
        assertTrue(batches.single().searchExhausted)

        repository.loadNext()
        advanceUntilIdle()

        assertEquals(1, batches.size)
        repository.clear()
    }

    @Test
    fun search_swapTab_findsFlaggedSwapAndFiltersOtherRecords() = runTest(timeout = 30.seconds) {
        val swap = flaggedSwap(0)
        val notSwap = record(1, hashPrefix = 0xAB.toByte())
        val (repository, batches) = repository(adapter(listOf(swap, notSwap)))

        repository.openSwapTab(searchQuery = "abababab")
        advanceUntilIdle()

        assertEquals(listOf(swap.uid), batches.last().records.map { it.uid })
        repository.clear()
    }
}
