package io.horizontalsystems.core

import java.util.Locale

interface ILanguageManager {
    val currentLanguage: String
    val currentLocaleTag: String
    val currentLocale: Locale
}
