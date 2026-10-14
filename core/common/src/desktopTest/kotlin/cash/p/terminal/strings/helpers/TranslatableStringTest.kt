package cash.p.terminal.strings.helpers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.login_logging_periods
import cash.p.terminal.resources.not_enough_ton_for_fee
import cash.p.terminal.strings.helpers.TranslatableString.PlainString
import cash.p.terminal.strings.helpers.TranslatableString.ResArrayString
import cash.p.terminal.strings.helpers.TranslatableString.ResString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TranslatableStringTest {

    @Test
    fun toString_resStringWithFormatArg_substitutesArgument() {
        val result = ResString(Res.string.not_enough_ton_for_fee, "12.5").toString()

        assertTrue(result.contains("12.5"))
        assertFalse(result.contains("%s"))
    }

    @Test
    fun toString_resArrayString_returnsItemAtIndex() {
        val first = ResArrayString(Res.array.login_logging_periods, 0).toString()
        val second = ResArrayString(Res.array.login_logging_periods, 1).toString()

        assertTrue(first.isNotBlank())
        assertNotEquals(first, second)
    }

    @Test
    fun toString_plainString_returnsText() {
        assertEquals("abc", PlainString("abc").toString())
    }
}
