package cash.p.terminal.core.managers

import cash.p.terminal.core.App
import cash.p.terminal.strings.helpers.LocaleHelper
import io.horizontalsystems.core.ILanguageManager
import java.util.*

class LanguageManager : ILanguageManager {

    val fallbackLocale by LocaleHelper::fallbackLocale

    // Not cached: the language can also be changed from the system app-language settings.
    var currentLocale: Locale
        get() = App.instance.getLocale()
        set(value) {
            App.instance.setLocale(value)
        }

    var currentLocaleTag: String
        get() = currentLocale.toLanguageTag()
        set(value) {
            currentLocale = Locale.forLanguageTag(value)
        }

    val currentLanguageName: String
        get() = getName(currentLocaleTag)

    override val currentLanguage: String
        get() = currentLocale.language

    fun getName(tag: String): String {
        return Locale.forLanguageTag(tag)
            .getDisplayName(currentLocale)
            .replaceFirstChar(Char::uppercase)
    }

    fun getNativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar(Char::uppercase)
    }

}
