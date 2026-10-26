package cash.p.terminal.modules.send.beam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class BeamAmountTest {
    @Test
    fun parse_exactDecimals_preservesAtomicUnits() {
        assertEquals(1L, BeamAmount.parse("0.00000001"))
        assertEquals(123456789L, BeamAmount.parse("1.23456789"))
        assertEquals(123456789L, BeamAmount.parse("1,23456789"))
        assertEquals(100000000L, BeamAmount.parse("1"))
        assertEquals(Long.MAX_VALUE, BeamAmount.parse("92233720368.54775807"))
    }

    @Test
    fun parse_invalidOrOverflowingAmount_rejectsWithoutRounding() {
        listOf("", "0", "-1", "+1", "1e2", "NaN", "1.000000001", "1.000000000",
            "92233720368.54775808", "92233720369", "1,234.56", " 1", "1 ", "9".repeat(100)
        ).forEach { assertNull(it, BeamAmount.parse(it)) }
    }

    @Test
    fun toAtomic_providerAmounts_acceptOnlyWholeGroth() {
        assertEquals(100000000L, BeamAmount.toAtomic(BigDecimal("1.000000000000")))
        assertEquals(1L, BeamAmount.toAtomic(BigDecimal("0.00000001")))
        listOf("0.000000001", "0", "-1", "92233720368.54775808").forEach {
            assertNull(it, BeamAmount.toAtomic(BigDecimal(it)))
        }
    }

    @Test
    fun format_atomicExtremes_preservesEightDecimalPlaces() {
        assertEquals("0.00000001", BeamAmount.format(1))
        assertEquals("92233720368.54775807", BeamAmount.format(Long.MAX_VALUE))
        assertEquals("0.00000000", BeamAmount.format(0))
    }
}
