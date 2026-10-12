package io.horizontalsystems.core

import java.util.Locale

class DesktopLanguageManager : ILanguageManager {
    override val currentLocale: Locale
        get() = Locale.getDefault()
    override val currentLocaleTag: String
        get() = currentLocale.toLanguageTag()
    override val currentLanguage: String
        get() = currentLocale.language
}
