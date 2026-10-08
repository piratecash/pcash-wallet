package cash.p.terminal.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SwapProviderTransactionsStorageTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(dispatcher)
    private val dao = mockk<SwapProviderTransactionsDao>(relaxed = true)
    private val storage = SwapProviderTransactionsStorage(
        dao = dao,
        dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)
    )

    private lateinit var database: AppDatabase
    private lateinit var roomStorage: SwapProviderTransactionsStorage

    private val bitcoinBip84 = bitcoinToken(TokenType.Derivation.Bip84)
    private val bitcoinBip44 = bitcoinToken(TokenType.Derivation.Bip44)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        roomStorage = SwapProviderTransactionsStorage(
            dao = database.swapProviderTransactionsDao(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun bitcoinToken(derivation: TokenType.Derivation) = Token(
        coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
        blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
        type = TokenType.Derived(derivation),
        decimals = 8,
    )

    private fun bitcoinInSwap(tokenQueryIdIn: String?) = swap().copy(
        coinUidIn = "bitcoin",
        blockchainTypeIn = BlockchainType.Bitcoin.uid,
        tokenQueryIdIn = tokenQueryIdIn,
    )

    private fun bitcoinOutSwap(tokenQueryIdOut: String?) = swap().copy(
        coinUidOut = "bitcoin",
        blockchainTypeOut = BlockchainType.Bitcoin.uid,
        tokenQueryIdOut = tokenQueryIdOut,
    )

    private fun getAllByAddressIn(token: Token) =
        roomStorage.getAll(token, "addr-in", statusesExcluded = emptyList(), limit = 10)

    private fun swap(
        date: Long = 1_000L,
        accountId: String = "acc-A",
    ) = SwapProviderTransaction(
        date = date,
        outgoingRecordUid = null,
        transactionId = "tx-$date",
        status = TransactionStatusEnum.NEW.name.lowercase(),
        provider = SwapProvider.CHANGENOW,
        coinUidIn = "binancecoin",
        blockchainTypeIn = "binance-smart-chain",
        amountIn = BigDecimal.ONE,
        addressIn = "addr-in",
        coinUidOut = "litecoin",
        blockchainTypeOut = "litecoin",
        amountOut = BigDecimal.TEN,
        addressOut = "addr-out",
        accountId = accountId,
    )

    private fun account(id: String) = Account(
        id = id,
        name = "Test",
        type = AccountType.EvmAddress("0x1"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    @Test
    fun observeAllByAccount_delegatesToDaoWithDefaultLimit() = runTest(dispatcher) {
        val transactions = listOf(swap(date = 1, accountId = "acc-A"))
        every { dao.observeAllByAccount("acc-A", 100) } returns flowOf(transactions)

        val result = storage.observeAllByAccount("acc-A").first()

        assertEquals(transactions, result)
        verify { dao.observeAllByAccount("acc-A", 100) }
    }

    @Test
    fun observeAllByAccount_returnsEmptyForUnknownAccount() = runTest(dispatcher) {
        every { dao.observeAllByAccount("unknown", 100) } returns flowOf(emptyList())

        val result = storage.observeAllByAccount("unknown").first()

        assertEquals(emptyList<SwapProviderTransaction>(), result)
    }

    @Test
    fun observeForActiveAccount_emitsTransactionsForActiveAccount() = runTest(dispatcher) {
        val txs = listOf(swap(date = 1, accountId = "acc-A"))
        every { dao.observeAllByAccount("acc-A", 100) } returns flowOf(txs)
        val accountFlow = MutableStateFlow<ActiveAccountState>(
            ActiveAccountState.ActiveAccount(account("acc-A"))
        )

        val result = storage.observeForActiveAccount(accountFlow).first()

        assertEquals(txs, result)
    }

    @Test
    fun observeForActiveAccount_emitsEmptyWhenNotLoaded() = runTest(dispatcher) {
        val accountFlow = MutableStateFlow<ActiveAccountState>(ActiveAccountState.NotLoaded)

        val result = storage.observeForActiveAccount(accountFlow).first()

        assertEquals(emptyList<SwapProviderTransaction>(), result)
    }

    @Test
    fun observeForActiveAccount_emitsEmptyWhenAccountNull() = runTest(dispatcher) {
        val accountFlow = MutableStateFlow<ActiveAccountState>(
            ActiveAccountState.ActiveAccount(null)
        )

        val result = storage.observeForActiveAccount(accountFlow).first()

        assertEquals(emptyList<SwapProviderTransaction>(), result)
    }

    @Test
    fun observeForActiveAccount_switchesWhenAccountChanges() = runTest(dispatcher) {
        val txsA = listOf(swap(date = 1, accountId = "acc-A"))
        val txsB = listOf(swap(date = 2, accountId = "acc-B"))
        every { dao.observeAllByAccount("acc-A", 100) } returns flowOf(txsA)
        every { dao.observeAllByAccount("acc-B", 100) } returns flowOf(txsB)

        val accountFlow = MutableStateFlow<ActiveAccountState>(
            ActiveAccountState.ActiveAccount(account("acc-A"))
        )

        val flow = storage.observeForActiveAccount(accountFlow)

        assertEquals(txsA, flow.first())

        accountFlow.value = ActiveAccountState.ActiveAccount(account("acc-B"))

        assertEquals(txsB, flow.first())
    }

    @Test
    fun getAllUnfinishedByAccount_delegatesToDao() {
        val transactions = listOf(swap(accountId = "acc-A"))
        val excluded = SwapProviderTransaction.FINISHED_STATUSES
        every { dao.getAllUnfinishedByAccount("acc-A", excluded, 10) } returns transactions

        val result = storage.getAllUnfinishedByAccount("acc-A", excluded, 10)

        assertEquals(transactions, result)
        verify { dao.getAllUnfinishedByAccount("acc-A", excluded, 10) }
    }

    @Test
    fun getAll_rowWithTokenQueryId_foundOnlyByItsToken() {
        val swap = bitcoinInSwap(tokenQueryIdIn = bitcoinBip84.tokenQuery.id)
        roomStorage.save(swap)

        assertEquals(listOf(swap), getAllByAddressIn(bitcoinBip84))
        assertEquals(emptyList<SwapProviderTransaction>(), getAllByAddressIn(bitcoinBip44))
    }

    @Test
    fun getAll_rowWithoutTokenQueryId_foundByCoinUidChainAndAddress() {
        val swap = bitcoinInSwap(tokenQueryIdIn = null)
        roomStorage.save(swap)

        assertEquals(listOf(swap), getAllByAddressIn(bitcoinBip44))
        assertEquals(
            emptyList<SwapProviderTransaction>(),
            roomStorage.getAll(bitcoinBip44, "other-address", statusesExcluded = emptyList(), limit = 10)
        )
    }

    @Test
    fun getByTokenOut_rowWithTokenQueryId_foundOnlyByItsToken() {
        val swap = bitcoinOutSwap(tokenQueryIdOut = bitcoinBip84.tokenQuery.id)
        roomStorage.save(swap)

        assertEquals(swap, roomStorage.getByTokenOut(bitcoinBip84, swap.date, "acc-A"))
        assertNull(roomStorage.getByTokenOut(bitcoinBip44, swap.date, "acc-A"))
    }

    @Test
    fun getByTokenOut_rowWithoutTokenQueryId_foundByCoinUidAndChain() {
        val swap = bitcoinOutSwap(tokenQueryIdOut = null)
        roomStorage.save(swap)

        assertEquals(swap, roomStorage.getByTokenOut(bitcoinBip44, swap.date, "acc-A"))
    }
}
