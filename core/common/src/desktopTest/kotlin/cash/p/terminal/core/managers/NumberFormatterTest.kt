package cash.p.terminal.core.managers

import io.horizontalsystems.core.ILanguageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class NumberFormatterTest {

    private val formatter = NumberFormatter(object : ILanguageManager {
        override var currentLocale: Locale = Locale.US
        override var currentLocaleTag: String = "en-US"
        override val currentLanguage: String = "en"
    })

    @Test
    fun formatFiatShort_millions_appendsSuffixFromResource() {
        assertEquals("$12.35M", formatter.formatFiatShort(BigDecimal("12345678"), "$", 2))
    }

    @Test
    fun formatFiatShort_belowThousand_hasNoSuffix() {
        assertEquals("$123.5", formatter.formatFiatShort(BigDecimal("123.456"), "$", 2))
    }

    @Test
    fun formatNumberShort_billions_appendsSuffixFromResource() {
        assertEquals("1.5B", formatter.formatNumberShort(BigDecimal("1500000000"), 2))
    }
}
