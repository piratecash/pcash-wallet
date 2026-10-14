package cash.p.terminal.modules.basecurrency

import cash.p.terminal.core.managers.Currencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CurrencyFlagsTest {

    @Test
    fun currencyFlag_everyCurrencyButUsd_hasItsOwnFlag() {
        val usdFlag = currencyFlag("USD")

        Currencies.all.filter { it.code != "USD" }.forEach {
            assertNotEquals("${it.code} falls back to the USD flag", usdFlag, currencyFlag(it.code))
        }
    }

    @Test
    fun currencyFlag_flagsAreUniquePerCurrency() {
        val flags = Currencies.all.map { currencyFlag(it.code) }

        assertEquals(flags.size, flags.distinct().size)
    }

    @Test
    fun currencyFlag_unknownCode_fallsBackToUsdFlag() {
        assertEquals(currencyFlag("USD"), currencyFlag("XXX"))
    }
}
