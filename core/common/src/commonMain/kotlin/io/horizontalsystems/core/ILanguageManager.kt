package io.horizontalsystems.core

import java.util.Locale

interface ILanguageManager {
    val currentLanguage: String
    var currentLocaleTag: String
    var currentLocale: Locale
}
