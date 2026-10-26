package cash.p.terminal.core.adapters.zcash

import cash.p.zcash.Balance
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ZcashBalanceDataTest {

    @Test
    fun toBalanceData_pendingBalance_mapsToProcessingBalance() {
        val balanceData = Balance(
            available = 100_000_000,
            changePending = 20_000_000,
            valuePending = 30_000_000
        ).toBalanceData()

        assertBigDecimalEquals("1", balanceData.available)
        assertBigDecimalEquals("0.5", balanceData.pending)
        assertBigDecimalEquals("1.5", balanceData.total)
    }

    @Test
    fun toBalanceData_availableBalance_excludesPending() {
        val balanceData = Balance(
            available = 0,
            changePending = 406_500_000,
            valuePending = 0
        ).toBalanceData()

        assertBigDecimalEquals("0", balanceData.available)
        assertBigDecimalEquals("4.065", balanceData.pending)
        assertBigDecimalEquals("4.065", balanceData.total)
    }

    @Test
    fun toBalanceData_reservedBalance_mapsToTimeLockedWithoutChangingTotal() {
        val balanceData = Balance(
            available = 100_000_000,
            locked = 200_000_000,
            changePending = 30_000_000,
        ).toBalanceData()

        assertBigDecimalEquals("1", balanceData.available)
        assertBigDecimalEquals("2", balanceData.timeLocked)
        assertBigDecimalEquals("0.3", balanceData.pending)
        assertBigDecimalEquals("3.3", balanceData.total)
    }

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros())
    }
}
