package cash.p.terminal.modules.send.tron

import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.wallet.Token
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.transaction.Fee
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SendTronFeeServiceTest {

    private val activated = Address.fromBase58("TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7")
    private val unactivated = Address.fromHex("410a38028ed6146aa29c687c052b233131468b6635")
    private val pendingEstimate = CompletableDeferred<List<Fee>>()
    private val adapter = mockk<ISendTronAdapter> {
        coEvery { estimateFee(any<BigDecimal>(), activated) } returns
            listOf(Fee.Bandwidth(points = 269, price = 1_000, source = Fee.Bandwidth.Source.Free))
        coEvery { estimateFee(any<BigDecimal>(), unactivated) } coAnswers { pendingEstimate.await() }
    }
    private val service = SendTronFeeService(adapter, mockk<Token> { every { decimals } returns 6 })

    @Test
    fun setTronAddress_newRecipientWhileEstimating_previousFeeIsNotPublished() = runBlocking {
        service.setAmount(BigDecimal.TEN)
        service.setTronAddress(activated)

        val estimating = async { service.setTronAddress(unactivated) }
        val published = withTimeout(TIMEOUT_MS) { service.stateFlow.first { it.fee == null } }
        pendingEstimate.complete(emptyList())
        estimating.await()

        assertNull(published.breakdown)
    }

    @Test
    fun setTronAddress_olderEstimateFinishesLast_keepsNewRecipientFee() = runBlocking {
        val olderEstimate = CompletableDeferred<List<Fee>>()
        coEvery { adapter.estimateFee(any<BigDecimal>(), unactivated) } coAnswers { olderEstimate.await() }
        service.setTronAddress(unactivated)

        val older = async(start = CoroutineStart.UNDISPATCHED) { service.setAmount(BigDecimal.ONE) }
        service.setTronAddress(activated)
        olderEstimate.complete(listOf(Fee.AccountActivation(1_000_000)))
        older.await()

        assertEquals(BigDecimal.ZERO, service.stateFlow.value.fee?.stripTrailingZeros())
        assertNull(service.stateFlow.value.breakdown?.activationFee)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
