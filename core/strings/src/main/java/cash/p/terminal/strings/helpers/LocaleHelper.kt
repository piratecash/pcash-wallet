package cash.p.terminal.strings.helpers

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import java.util.Locale

// The app language is owned by LocaleManager on API 33+. Below it the platform has no storage,
// so the preference keeps it and AppCompat applies it. AppCompat's autoStoreLocales is not used:
// its one-time sync to LocaleManager can overwrite a locale set before the first activity.
object LocaleHelper {

    val fallbackLocale: Locale = Locale.ENGLISH

    // The file name is data (it predates the per-app API), so it stays a literal.
    private const val PREFERENCES_NAME = "cash.p.terminal.strings.helpers.LocaleHelper"
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"

    private val supportedLanguageTags: Set<String> by lazy {
        LocaleType.values().mapTo(hashSetOf()) { it.tag }
    }
    private val RTL: Set<String> by lazy {
        hashSetOf(
            "ar",
            "dv",
            "fa",
            "ha",
            "he",
            "iw",
            "ji",
            "ps",
            "sd",
            "ug",
            "ur",
            "yi"
        )
    }

    /** Localizes a non-activity context on API <= 32, where the platform does not apply per-app locales. */
    fun onAttach(context: Context): Context {
        val locales = appCompatLocales()
        if (locales == null) {
            syncDefaultLocales(context)
            return context
        }
        val primary = locales[0] ?: return context

        Locale.setDefault(primary)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(configuration)
    }

    fun getLocale(context: Context): Locale =
        displayedLocales(context).firstSupported()?.let { Locale.forLanguageTag(it.supportedLanguageTag()) }
            ?: fallbackLocale

    /** Must run on the main thread: on API <= 32 AppCompat recreates the running activities. */
    fun setLocale(context: Context, locale: Locale) {
        if (Build.VERSION.SDK_INT < 33) {
            // Synchronous: the language picker restarts the process right after this call.
            preferences(context).edit(commit = true) { putString(SELECTED_LANGUAGE, locale.toLanguageTag()) }
        }
        applyApplicationLocales(context, LocaleListCompat.create(locale))
    }

    /** Must run on the main thread, see [setLocale]. */
    fun resetLocale(context: Context) {
        preferences(context).edit { clear() }
        applyApplicationLocales(context, LocaleListCompat.getEmptyLocaleList())
    }

    /** Runs at process start: hands the stored locale to AppCompat, or moves it to LocaleManager once. */
    fun restoreLocale(context: Context) {
        val preferences = preferences(context)
        val tag = preferences.getString(SELECTED_LANGUAGE, null) ?: return
        if (Build.VERSION.SDK_INT < 33) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            return
        }
        if (localeManager(context).applicationLocales.isEmpty) {
            applyApplicationLocales(context, LocaleListCompat.forLanguageTags(tag))
        }
        preferences.edit { remove(SELECTED_LANGUAGE) }
    }

    /** CMP reads the first default locale, which each activity launch may reset to an unsupported language. */
    fun syncDefaultLocales(context: Context) {
        val primary = displayedLocales(context).firstSupported() ?: fallbackLocale
        val defaults = LocaleList.getDefault()
        val others = (0 until defaults.size()).map(defaults::get).filter { it != primary }
        val tags = (listOf(primary) + others).joinToString(",") { it.toLanguageTag() }
        LocaleList.setDefault(LocaleList.forLanguageTags(tags))
    }

    fun isRTL(locale: Locale): Boolean {
        return RTL.contains(locale.language)
    }

    // API 33+ applies the app locales to the configuration; below it onAttach has already localized it.
    private fun displayedLocales(context: Context): LocaleListCompat =
        appCompatLocales() ?: LocaleListCompat.wrap(context.resources.configuration.locales)

    private fun appCompatLocales(): LocaleListCompat? =
        if (Build.VERSION.SDK_INT < 33) {
            AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }
        } else {
            null
        }

    // AppCompatDelegate reaches LocaleManager only through a live activity, which may not exist yet.
    private fun applyApplicationLocales(context: Context, locales: LocaleListCompat) {
        if (Build.VERSION.SDK_INT >= 33) {
            localeManager(context).applicationLocales = LocaleList.forLanguageTags(locales.toLanguageTags())
        } else {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    @RequiresApi(33)
    private fun localeManager(context: Context): LocaleManager =
        context.getSystemService(LocaleManager::class.java)

    private fun LocaleListCompat.firstSupported(): Locale? =
        (0 until size()).mapNotNull(::get).firstOrNull { it.supportedLanguageTag() in supportedLanguageTags }

    private fun Locale.supportedLanguageTag(): String {
        val tag = toLanguageTag()
        return if (tag.contains("-") && tag != LocaleType.pt_br.tag) {
            tag.substringBefore("-")
        } else {
            tag
        }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

}

enum class LocaleType(val tag: String) {
    de("de"),
    en("en"),
    es("es"),
    pt_br("pt-BR"),
    pt("pt"),
    fa("fa"),
    fr("fr"),
    ko("ko"),
    ru("ru"),
    uk("uk"),
    tr("tr"),
    zh("zh");
}
