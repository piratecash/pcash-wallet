package cash.p.terminal.core.adapters

import cash.p.terminal.core.managers.TronKitWrapper
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.TransferContract
import io.horizontalsystems.tronkit.transaction.Fee
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

// The fee is estimated on Dispatchers.Default, so these tests wait in real time.
class TronAdapterTest {

    private val balance = BigInteger.valueOf(10_000_000)
    private val tronKit = mockk<TronKit>(relaxed = true) {
        every { trxBalance } returns balance
        every { trxBalanceFlow } returns MutableStateFlow(balance)
        every { address } returns Address.fromBase58(OWN_ADDRESS)
        every { transferContract(any(), any()) } returns mockk<TransferContract>()
        every { accountActivationFees() } returns listOf(
            Fee.Bandwidth(points = 269, price = 1_000, burnInSuns = 100_000),
            Fee.AccountActivation(1_000_000),
        )
    }
    private val adapter = TronAdapter(TronKitWrapper(tronKit, signer = null))

    @After
    fun tearDown() {
        adapter.stop()
    }

    @Test
    fun maxSpendableBalance_bandwidthBurned_reservesEstimatedFee() = runBlocking {
        coEvery { tronKit.estimateFee(any<Contract>()) } returns listOf(Fee.Bandwidth(points = 269, price = 1_000))

        adapter.resumeNetwork()
        withTimeout(TIMEOUT_MS) { adapter.fee.first { it.compareTo(BigDecimal("0.269")) == 0 } }

        assertEquals(0, BigDecimal("9.731").compareTo(adapter.maxSpendableBalance))
    }

    @Test
    fun maxSpendableBalance_freeBandwidthCovers_spendsWholeBalance() = runBlocking {
        coEvery { tronKit.estimateFee(any<Contract>()) } returns
            listOf(Fee.Bandwidth(points = 269, price = 1_000, source = Fee.Bandwidth.Source.Free))

        adapter.resumeNetwork()
        withTimeout(TIMEOUT_MS) { adapter.fee.first { it.signum() == 0 } }

        assertEquals(0, BigDecimal.TEN.compareTo(adapter.maxSpendableBalance))
    }

    @Test
    fun maxSpendableBalance_estimateFailed_reservesActivationFees() = runBlocking {
        coEvery { tronKit.estimateFee(any<Contract>()) } throws IllegalStateException("node unavailable")

        adapter.resumeNetwork()
        coVerify(timeout = TIMEOUT_MS) { tronKit.estimateFee(any<Contract>()) }

        assertEquals(0, BigDecimal("8.9").compareTo(adapter.maxSpendableBalance))
    }

    private companion object {
        const val OWN_ADDRESS = "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7"
        const val TIMEOUT_MS = 10_000L
    }
}
