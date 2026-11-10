package cash.p.terminal.modules.send.ton

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.TonFeeEstimate
import io.horizontalsystems.tonkit.FriendlyAddress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The service debounces on Dispatchers.Default with real delays, so these tests wait in real time.
class SendTonFeeServiceTest {

    private val adapter = mockk<ISendTonAdapter>()
    private val address = mockk<FriendlyAddress>(relaxed = true)
    private val estimate = TonFeeEstimate(fee = BigDecimal("0.01"), requiredTon = BigDecimal("0.075"))
    private val failing = AtomicBoolean(false)
    private val service = SendTonFeeService(adapter)

    @After
    fun tearDown() {
        service.close()
    }

    @Test
    fun estimateFee_failsAfterInputsChange_clearsPreviousSuccessUntilRetrySucceeds() = runBlocking {
        coEvery { adapter.estimateFee(any(), any(), any()) } answers {
            if (failing.get()) throw IllegalStateException("emulation failed")
            estimate
        }
        service.setTonAddress(address)
        service.setAmount(BigDecimal.ONE)
        awaitState { it.feeStatus is FeeStatus.Success && !it.inProgress }
        assertEquals(FeeStatus.Success(estimate.fee, estimate.requiredTon), service.stateFlow.value.feeStatus)

        failing.set(true)
        service.setAmount(BigDecimal.TEN)
        val failed = awaitState { it.feeStatus == null }
        assertNull(failed.quote)

        failing.set(false)
        val recovered = awaitState { it.feeStatus is FeeStatus.Success && !it.inProgress }
        assertEquals(BigDecimal.TEN, recovered.quote?.amount)
    }

    @Test
    fun estimateFee_nftEstimator_quotesNftTransferInsteadOfCoinSend() = runBlocking {
        coEvery { adapter.estimateNftTransferFee(NFT, address, "hi") } returns estimate

        SendTonFeeService(NftTransferFeeEstimator(adapter, NFT)).use { nftService ->
            nftService.setTonAddress(address)
            nftService.setMemo("hi")
            nftService.setAmount(ATTACHED)

            val state = awaitState(nftService) { it.feeStatus is FeeStatus.Success && !it.inProgress }

            assertEquals(FeeStatus.Success(estimate.fee, estimate.requiredTon), state.feeStatus)
            assertEquals(TonFeeQuote(estimate.fee, ATTACHED, address, "hi"), state.quote)
        }
        coVerify(exactly = 0) { adapter.estimateFee(any(), any(), any()) }
    }

    private suspend fun awaitState(
        target: SendTonFeeService = service,
        predicate: (SendTonFeeService.State) -> Boolean,
    ) = withTimeout(TIMEOUT_MS) { target.stateFlow.first(predicate) }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val NFT = "nft-address"
        val ATTACHED = BigDecimal("0.05")
    }
}
