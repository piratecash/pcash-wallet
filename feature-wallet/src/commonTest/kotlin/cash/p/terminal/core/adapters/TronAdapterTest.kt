package cash.p.terminal.core.adapters

import cash.p.terminal.core.managers.TronKitWrapper
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.transaction.Fee
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class TronAdapterTest {

    private val tronKit = mockk<TronKit>(relaxed = true) {
        every { accountActivationFees() } returns listOf(
            Fee.Bandwidth(points = 100, price = 1_000),
            Fee.AccountActivation(amount = 1_000_000),
        )
    }
    private val adapter = TronAdapter(TronKitWrapper(tronKit, signer = null))

    @Test
    fun maxSpendableBalance_networkNeverStarted_reservesAccountActivationFees() {
        every { tronKit.trxBalance } returns BigInteger.valueOf(786_487_400)

        assertEquals(0, BigDecimal("785.3874").compareTo(adapter.maxSpendableBalance))
    }

    @Test
    fun maxSpendableBalance_balanceBelowReserve_returnsZero() {
        every { tronKit.trxBalance } returns BigInteger.valueOf(500_000)

        assertEquals(0, BigDecimal.ZERO.compareTo(adapter.maxSpendableBalance))
    }
}
