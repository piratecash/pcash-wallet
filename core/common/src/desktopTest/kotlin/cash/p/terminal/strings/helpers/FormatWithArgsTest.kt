package cash.p.terminal.strings.helpers

import io.horizontalsystems.core.formatWithArgs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatWithArgsTest {

    private fun <T> withPersianLocale(block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fa"))
        try {
            return block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun formatWithArgs_sequentialIntegerInPersianLocale_usesLocalizedDigits() {
        assertEquals("(۳۰)", withPersianLocale { "(%d)".formatWithArgs(arrayOf(30)) })
    }

    @Test
    fun formatWithArgs_positionalIntegerInPersianLocale_usesLocalizedDigits() {
        assertEquals("۷", withPersianLocale { "%1\$d".formatWithArgs(arrayOf(7)) })
    }

    @Test
    fun formatWithArgs_stringConversionInPersianLocale_isNotLocalized() {
        assertEquals("30", withPersianLocale { "%s".formatWithArgs(arrayOf(30)) })
    }
}
