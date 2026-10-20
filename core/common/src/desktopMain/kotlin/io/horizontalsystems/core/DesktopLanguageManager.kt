package io.horizontalsystems.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class DesktopLanguageManager(private val settingsStore: DesktopSettingsStore) : ILanguageManager {

    // Captured before the stored language is applied, so a reset can return to it.
    private val systemLocale: Locale = Locale.getDefault()

    private val storedLocale: Locale? = settingsStore.languageTag?.let(Locale::forLanguageTag)

    private val _localeTag = MutableStateFlow((storedLocale ?: systemLocale).toLanguageTag())
    val localeTag: StateFlow<String> = _localeTag.asStateFlow()

    init {
        // Everything reading Locale.getDefault() (request headers, formatters) must see the stored choice.
        storedLocale?.let(::apply)
    }

    override var currentLocaleTag: String
        get() = _localeTag.value
        set(value) {
            settingsStore.languageTag = value
            apply(Locale.forLanguageTag(value))
        }

    override var currentLocale: Locale
        get() = Locale.forLanguageTag(currentLocaleTag)
        set(value) {
            currentLocaleTag = value.toLanguageTag()
        }

    override val currentLanguage: String
        get() = currentLocale.language

    /** Returns to the system language: the stored one goes away with the rest of the data. */
    fun reset() {
        settingsStore.languageTag = null
        apply(systemLocale)
    }

    private fun apply(locale: Locale) {
        Locale.setDefault(locale)
        _localeTag.value = locale.toLanguageTag()
    }
}
