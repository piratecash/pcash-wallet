package cash.p.terminal.modules.send.zcash

import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ZcashSendBalanceTest {

    @Test
    fun getZcashSdkBalance_adapterAvailable_returnsAdapterAvailable() {
        val wallet = mockk<Wallet>()
        val balanceAdapter = mockk<IBalanceAdapter> {
            every { balanceData } returns BalanceData(available = BigDecimal("4.065"))
        }
        val adapterManager = mockk<IAdapterManager> {
            every { getBalanceAdapterForWallet(wallet) } returns balanceAdapter
        }

        val sdkBalance = adapterManager.getZcashSdkBalance(
            wallet = wallet,
            fallback = BigDecimal("3")
        )

        assertBigDecimalEquals("4.065", sdkBalance)
    }

    @Test
    fun getZcashSdkBalance_missingAdapter_returnsFallback() {
        val wallet = mockk<Wallet>()
        val adapterManager = mockk<IAdapterManager> {
            every { getBalanceAdapterForWallet(wallet) } returns null
        }

        val sdkBalance = adapterManager.getZcashSdkBalance(
            wallet = wallet,
            fallback = BigDecimal("3")
        )

        assertBigDecimalEquals("3", sdkBalance)
    }

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros())
    }
}
