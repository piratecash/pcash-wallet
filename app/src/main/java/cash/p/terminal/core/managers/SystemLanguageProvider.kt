package cash.p.terminal.core.managers

import android.content.res.Resources

/**
 * The app rewrites the JVM default locale via [java.util.Locale.setDefault] when the user
 * picks an interface language, so [java.util.Locale.getDefault] no longer reflects the device
 * language. System resources are read from the untouched global configuration instead.
 */
class SystemLanguageProviderImpl : SystemLanguageProvider {
    override val language: String
        get() = Resources.getSystem().configuration.locales.get(0).language
}
