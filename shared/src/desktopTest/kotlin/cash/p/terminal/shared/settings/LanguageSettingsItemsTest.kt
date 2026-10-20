package cash.p.terminal.shared.settings

import cash.p.terminal.strings.helpers.LocaleType
import io.horizontalsystems.core.ILanguageManager
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageSettingsItemsTest {

    @Test
    fun languageSettingsItems_regionalLocale_marksItsLanguageAsCurrent() {
        assertEquals(LocaleType.en.tag, currentTagFor("en-US"))
        assertEquals(LocaleType.zh.tag, currentTagFor("zh-CN"))
        assertEquals(LocaleType.pt.tag, currentTagFor("pt-PT"))
        assertEquals(LocaleType.pt_br.tag, currentTagFor("pt-BR"))
        assertEquals(LocaleType.ru.tag, currentTagFor("ru"))
    }

    @Test
    fun languageSettingsItems_unsupportedLocale_marksNothingAsCurrent() {
        assertEquals(0, languageSettingsItems(languageManager("ja-JP")).count { it.current })
    }

    @Test
    fun currentLanguageName_regionalLocale_namesTheTickedLanguage() {
        assertEquals("English", currentLanguageName(languageManager("en-VN")))
        assertEquals("Русский", currentLanguageName(languageManager("ru")))
    }

    @Test
    fun currentLanguageName_unsupportedLocale_namesTheLocaleItself() {
        assertEquals("日本語 (日本)", currentLanguageName(languageManager("ja-JP")))
    }

    private fun currentTagFor(localeTag: String): String =
        languageSettingsItems(languageManager(localeTag)).single { it.current }.tag

    private fun languageManager(localeTag: String) = object : ILanguageManager {
        override val currentLanguage: String get() = currentLocale.language
        override var currentLocaleTag: String = localeTag
        override var currentLocale: Locale = Locale.forLanguageTag(localeTag)
    }
}
