package cash.p.terminal.modules.evmfee

import cash.p.terminal.ui_compose.entities.DataState
import io.horizontalsystems.ethereumkit.api.models.AccountState
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.subjects.SingleSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EvmFeeServiceTest {

    private val evmKit = mockk<EthereumKit>()
    private val gasDataService = mockk<EvmCommonGasDataService>()
    private val gasPrice = GasPrice.Legacy(1)
    private val gasPriceService = TestGasPriceService(
        DataState.Success(
            GasPriceInfo(gasPrice, gasPrice, default = true, warnings = emptyList(), errors = emptyList())
        )
    )
    private val dataA = TransactionData(Address(ADDRESS_A), BigInteger.ONE, byteArrayOf())
    private val dataB = TransactionData(Address(ADDRESS_B), BigInteger.ONE, byteArrayOf())
    private val estimateA = PendingEstimate()
    private val estimateB = SingleSubject.create<GasData>()
    private val service = EvmFeeService(evmKit, gasPriceService, gasDataService, dataA)

    @Before
    fun setUp() {
        every { evmKit.accountState } returns AccountState(BALANCE, 0)
        every { gasDataService.estimatedGasDataAsync(any(), dataA, any(), any()) } returns estimateA.single
        every { gasDataService.estimatedGasDataAsync(any(), dataB, any(), any()) } returns estimateB
    }

    @After
    fun tearDown() {
        service.clear()
    }

    @Test
    fun setTransactionData_newEstimatePending_invalidatesPreviousTransaction() = runBlocking {
        startWithSendableA()

        service.setTransactionData(dataB)

        assertEquals(DataState.Loading, service.transactionStatusFlow.replayCache.last())
    }

    @Test
    fun setTransactionData_newEstimateCompletes_emitsTransactionForNewData() = runBlocking {
        startWithSendableA()

        service.setTransactionData(dataB)
        estimateB.onSuccess(gasData())

        awaitSendableTransactionFor(dataB)
    }

    @Test
    fun setTransactionData_staleEstimateCompletesAfterReplacement_keepsNewState() = runBlocking {
        service.start()
        estimateA.awaitSubscribed()
        service.setTransactionData(dataB)

        estimateA.complete(gasData())

        assertEquals(DataState.Loading, service.transactionStatusFlow.replayCache.lastOrNull())

        estimateB.onSuccess(gasData())

        awaitSendableTransactionFor(dataB)
    }

    @Test
    fun setTransactionData_gasPriceLoading_cancelsPreviousEstimate() = runBlocking {
        service.start()
        estimateA.awaitSubscribed()
        gasPriceService.update(DataState.Loading)
        withTimeout(TIMEOUT_MS) { service.transactionStatusFlow.first { it == DataState.Loading } }

        service.setTransactionData(dataB)

        assertTrue(estimateA.disposed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun setTransactionData_maxSend_estimatedForKeepsRequestedInstance() = runBlocking {
        val maxData = TransactionData(Address(ADDRESS_B), BALANCE, byteArrayOf())
        every { gasDataService.estimatedGasDataAsync(any(), maxData, any(), any()) } returns Single.just(gasData())
        service.start()

        service.setTransactionData(maxData)

        val transaction = withTimeout(TIMEOUT_MS) {
            service.transactionStatusFlow.first { it.dataOrNull?.estimatedFor === maxData }
        }.dataOrNull
        assertEquals(BALANCE - BigInteger.valueOf(GAS_LIMIT), transaction?.transactionData?.value)
        assertEquals(emptyList<Throwable>(), transaction?.errors)
    }

    @Test
    fun setTransactionData_previousEstimateBlocksItsThread_invalidatesWithoutWaiting() = runBlocking {
        val subscribed = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { gasDataService.estimatedGasDataAsync(any(), dataA, any(), any()) } returns Single.fromCallable {
            subscribed.countDown()
            release.await()
            gasData()
        }
        service.start()
        assertTrue(subscribed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))

        launch(Dispatchers.Default) { service.setTransactionData(dataB) }
        val loading = withTimeoutOrNull(TIMEOUT_MS) {
            service.transactionStatusFlow.first { it == DataState.Loading }
        }
        release.countDown()

        assertEquals(DataState.Loading, loading)
    }

    private suspend fun startWithSendableA() {
        service.start()
        estimateA.complete(gasData())
        awaitSendableTransactionFor(dataA)
    }

    private suspend fun awaitSendableTransactionFor(data: TransactionData) {
        withTimeout(TIMEOUT_MS) {
            service.transactionStatusFlow.first {
                val transaction = it.dataOrNull
                transaction?.estimatedFor === data &&
                    transaction.transactionData == data &&
                    transaction.errors.isEmpty()
            }
        }
    }

    private fun gasData() = GasData(gasLimit = GAS_LIMIT, gasPrice = gasPrice)

    private class TestGasPriceService(private var state: DataState<GasPriceInfo>) : IEvmGasPriceService() {
        override fun createState() = state
        override fun setRecommended() = Unit
        override fun start() = Unit

        fun update(state: DataState<GasPriceInfo>) {
            this.state = state
            emitState()
        }
    }

    // Unlike a subject, reports both its subscription and its disposal.
    private class PendingEstimate {
        private val subscribed = CountDownLatch(1)
        val disposed = CountDownLatch(1)

        @Volatile
        private var emitter: SingleEmitter<GasData>? = null
        val single: Single<GasData> = Single.create { newEmitter ->
            newEmitter.setCancellable { disposed.countDown() }
            emitter = newEmitter
            subscribed.countDown()
        }

        fun awaitSubscribed() = assertTrue(subscribed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))

        fun complete(gasData: GasData) {
            awaitSubscribed()
            checkNotNull(emitter).onSuccess(gasData)
        }
    }

    private companion object {
        const val ADDRESS_A = "0x1111111111111111111111111111111111111111"
        const val ADDRESS_B = "0x2222222222222222222222222222222222222222"
        const val GAS_LIMIT = 21_000L
        const val TIMEOUT_MS = 5_000L
        val BALANCE: BigInteger = BigInteger.TEN.pow(18)
    }
}
