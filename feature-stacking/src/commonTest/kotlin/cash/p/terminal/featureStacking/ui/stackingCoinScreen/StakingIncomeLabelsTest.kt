package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import cash.p.terminal.featureStacking.ui.StakingChartDisplayMode
import cash.p.terminal.featureStacking.ui.StakingIncomePoint
import cash.p.terminal.featureStacking.ui.StakingIncomeSnapshot
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class StakingIncomeLabelsTest {

    private val numberFormatter = mockk<IAppNumberFormatter> {
        every { formatNumberShort(any(), any()) } answers { firstArg<BigDecimal>().toPlainString() }
        every { formatFiatFull(any(), any()) } answers { secondArg<String>() + firstArg<BigDecimal>().toPlainString() }
    }

    private val rub = Currency("RUB", "₽", 2)
    private val point = StakingIncomePoint(0, BigDecimal("39.1"), BigDecimal("150"))

    @Test
    fun labels_coinsMode_showsAmountWithTickerCasePreserved() {
        assertEquals("39.1 COSA" to null, snapshot(StakingChartDisplayMode.COINS).label(point, ticker = "COSA"))
    }

    @Test
    fun labels_fiatMode_usesSnapshotCurrencySymbol() {
        assertEquals("₽150" to null, snapshot(StakingChartDisplayMode.FIAT).label(point))
    }

    @Test
    fun labels_bothModeWithFiat_showsCoinsAndFiat() {
        assertEquals("39.1 PIRATE" to "₽150", snapshot(StakingChartDisplayMode.BOTH).label(point))
    }

    @Test
    fun labels_bothModeWithoutFiat_showsNoEstimate() {
        val label = snapshot(StakingChartDisplayMode.BOTH).label(point.copy(fiat = null))

        assertEquals("39.1 PIRATE" to "no estimate", label)
    }

    private fun snapshot(mode: StakingChartDisplayMode) = StakingIncomeSnapshot(rub, mode, listOf(point))

    private fun StakingIncomeSnapshot.label(point: StakingIncomePoint, ticker: String = "PIRATE") =
        labels(point, ticker, "no estimate", numberFormatter)
}
