package cash.p.terminal.modules.amount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class HSAmountInputTest {
    @Test fun setExternalCoinAmount_currencyInput_switchesToCoinAndPreservesAmount() {
        val viewModel = AmountInputViewModel2("DASH", 8, 2, AmountInputType.CURRENCY)
        var switches = 0

        viewModel.setExternalCoinAmount(BigDecimal("0.26537240"), AmountInputType.CURRENCY) { switches++ }

        assertEquals(1, switches)
        assertEquals(BigDecimal("0.26537240"), viewModel.coinAmount)
        assertEquals("0.26537240", viewModel.getEnterAmount())
    }

    @Test fun isValid_currencyManyDecimalsWithDot_returnsTrue() = assertTrue(AmountInputViewModel2("DASH", 8, 2, AmountInputType.CURRENCY).isValid("0.00000001"))

    @Test fun isValid_currencyManyDecimalsWithComma_returnsTrue() = assertTrue(AmountInputViewModel2("DASH", 8, 2, AmountInputType.CURRENCY).isValid("0,00000001"))

    @Test fun isValid_coinDecimalsOverLimitWithDot_returnsFalse() = assertFalse(AmountInputViewModel2("DASH", 8, 2, AmountInputType.COIN).isValid("0.123456789"))

    @Test fun isValid_coinDecimalsOverLimitWithComma_returnsFalse() = assertFalse(AmountInputViewModel2("DASH", 8, 2, AmountInputType.COIN).isValid("0,123456789"))

    @Test fun isValid_coinDecimalsAtLimitWithComma_returnsTrue() = assertTrue(AmountInputViewModel2("DASH", 8, 2, AmountInputType.COIN).isValid("0,12345678"))
}
