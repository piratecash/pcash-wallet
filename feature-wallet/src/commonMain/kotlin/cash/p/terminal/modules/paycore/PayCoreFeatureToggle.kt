package cash.p.terminal.modules.paycore

import cash.p.terminal.core.managers.SystemLanguageProvider
import cash.p.terminal.core.providers.SharedAppConfig
import io.horizontalsystems.core.ILanguageManager

class PayCoreFeatureToggle(
    private val languageManager: ILanguageManager,
    private val systemLanguageProvider: SystemLanguageProvider,
) {
    fun isEnabled(): Boolean {
        if (!SharedAppConfig.isPayCoreEnabled) return false
        val appLanguage = languageManager.currentLanguage
        val systemLanguage = systemLanguageProvider.language
        return appLanguage != UKRAINIAN && systemLanguage != UKRAINIAN
    }

    private companion object {
        const val UKRAINIAN = "uk"
    }
}
