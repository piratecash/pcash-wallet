package cash.p.terminal.modules.evmfee.eip1559

import cash.p.terminal.modules.evmfee.GasPriceInfo
import cash.p.terminal.ui_compose.entities.DataState
import io.horizontalsystems.ethereumkit.core.eip1559.Eip1559GasPriceProvider
import io.horizontalsystems.ethereumkit.core.eip1559.FeeHistory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class Eip1559GasPriceServiceTest {

    private val gasProvider = mockk<Eip1559GasPriceProvider>()
    private val service = Eip1559GasPriceService(gasProvider, Flowable.empty())
    private val collectorScope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        collectorScope.cancel()
    }

    @Test
    fun retry_customPriceLostToError_recoversToRecommended() = runBlocking {
        val pendingFetch = SingleSubject.create<FeeHistory>()
        stubFetches(pendingFetch, Single.just(feeHistory))
        service.start()
        service.setGasPrice(maxFee = 30, priorityFee = 3)
        pendingFetch.onError(IOException("timeout"))
        awaitState { it is DataState.Error }

        service.retry()

        val info = awaitState { it is DataState.Success }.dataOrNull
        assertEquals(RECOMMENDED_MAX_FEE, info?.gasPrice?.max)
        assertEquals(true, info?.default)
    }

    @Test
    fun retry_twice_lateFailureDoesNotOverwriteSuccess() = runBlocking {
        val firstRetryFetch = SingleSubject.create<FeeHistory>()
        val secondRetryFetch = SingleSubject.create<FeeHistory>()
        stubFetches(Single.error(IOException("timeout")), firstRetryFetch, secondRetryFetch)
        service.start()
        awaitState { it is DataState.Error }

        service.retry()
        service.retry()
        firstRetryFetch.onSuccess(feeHistory)
        awaitState { it is DataState.Success }
        secondRetryFetch.onError(IOException("timeout"))

        assertEquals(RECOMMENDED_MAX_FEE, service.stateFlow.value.dataOrNull?.gasPrice?.max)
        verify(exactly = 2) { gasProvider.feeHistorySingle(any(), any(), any()) }
    }

    @Test
    fun setRecommended_cancelledFetchFails_doesNotOverwriteNewerSuccess() = runBlocking {
        val cancelledFetch = SingleSubject.create<FeeHistory>()
        val newerFetch = SingleSubject.create<FeeHistory>()
        stubFetches(cancelledFetch, newerFetch)
        val states = recordStates()
        service.start()
        // Cancelling a fetch that has not started yet would never reach its error handling.
        awaitSubscribed(cancelledFetch)

        service.setRecommended()
        newerFetch.onSuccess(feeHistory)
        awaitState { it is DataState.Success }
        cancelledFetch.onError(IOException("timeout"))

        assertTrue(states.none { it is DataState.Error })
        assertEquals(RECOMMENDED_MAX_FEE, service.stateFlow.value.dataOrNull?.gasPrice?.max)
    }

    private fun stubFetches(vararg fetches: Single<FeeHistory>) {
        every { gasProvider.feeHistorySingle(any(), any(), any()) } returnsMany fetches.toList()
    }

    private fun recordStates(): List<DataState<GasPriceInfo>> {
        val states = CopyOnWriteArrayList<DataState<GasPriceInfo>>()
        collectorScope.launch { service.stateFlow.collect { states += it } }
        return states
    }

    private suspend fun awaitSubscribed(fetch: SingleSubject<FeeHistory>) = withTimeout(TIMEOUT_MS) {
        while (!fetch.hasObservers()) delay(POLL_MS)
    }

    private suspend fun awaitState(predicate: (DataState<GasPriceInfo>) -> Boolean) =
        withTimeout(TIMEOUT_MS) { service.stateFlow.first(predicate) }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
        val feeHistory = FeeHistory(
            baseFeePerGas = listOf(100L),
            gasUsedRatio = listOf(0.5),
            oldestBlock = 1L,
            reward = listOf(listOf(20L))
        )
        // (base 100 + tip 20) with the 125% headroom.
        const val RECOMMENDED_MAX_FEE = 150L
    }
}
