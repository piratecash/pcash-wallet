package cash.p.terminal.shared.desktop

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSystemLanguageProviderTest {

    private val originalLocale = Locale.getDefault()

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun language_appLanguageAppliedToDefaultLocale_returnsOsLanguage() {
        val osLanguage = System.getProperty("user.language")
        val appLocale = if (osLanguage == "uk") Locale.ENGLISH else Locale.forLanguageTag("uk")

        Locale.setDefault(appLocale)

        assertEquals(osLanguage, DesktopSystemLanguageProvider.language)
    }
}
