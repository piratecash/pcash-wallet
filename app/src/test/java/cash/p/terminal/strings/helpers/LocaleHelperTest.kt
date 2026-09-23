package cash.p.terminal.strings.helpers

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.LayoutDirection
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocaleManager
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocaleHelperTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val originalLocale = Locale.getDefault()
    private val originalLocaleList = LocaleList.getDefault()
    private val preferences =
        application.getSharedPreferences("cash.p.terminal.strings.helpers.LocaleHelper", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        ShadowLocaleManager.reset()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        preferences.edit(commit = true) { clear() }
        LocaleList.setDefault(originalLocaleList)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun restoreLocale_storedTagAndNoAppLocales_movesTagToLocaleManager() {
        setStoredTag("pt-BR")

        LocaleHelper.restoreLocale(application)

        assertEquals("pt-BR", frameworkAppLocales())
        assertFalse(preferences.contains(SELECTED_LANGUAGE))
    }

    @Test
    fun restoreLocale_appLocalesAlreadySet_keepsAppLocalesAndDropsStoredTag() {
        localeManager().applicationLocales = LocaleList.forLanguageTags("de")
        setStoredTag("ru")

        LocaleHelper.restoreLocale(application)

        assertEquals("de", frameworkAppLocales())
        assertFalse(preferences.contains(SELECTED_LANGUAGE))
    }

    @Test
    fun restoreLocale_noStoredTag_leavesAppLocalesEmpty() {
        LocaleHelper.restoreLocale(application)

        assertEquals("", frameworkAppLocales())
    }

    @Test
    @Config(sdk = [32])
    fun restoreLocale_sdk32StoredTag_appliesToAppCompatAndKeepsTag() {
        setStoredTag("ru")

        LocaleHelper.restoreLocale(application)

        assertEquals("ru", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertTrue(preferences.contains(SELECTED_LANGUAGE))
    }

    @Test
    @Config(sdk = [32])
    fun setLocale_sdk32_storesTagForNextStart() {
        LocaleHelper.setLocale(application, Locale.forLanguageTag("uk"))

        assertEquals("uk", preferences.getString(SELECTED_LANGUAGE, null))
        assertEquals("uk", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun getLocale_unsupportedThenSupportedSystemLocale_returnsFirstSupported() {
        assertEquals(Locale.forLanguageTag("ru"), LocaleHelper.getLocale(contextWith("it-IT,ru-RU")))
    }

    @Test
    fun getLocale_onlyUnsupportedLocales_returnsFallback() {
        assertEquals(LocaleHelper.fallbackLocale, LocaleHelper.getLocale(contextWith("nl-NL,it")))
    }

    @Test
    fun getLocale_regionalVariants_keepsOnlyBrazilianRegion() {
        assertEquals(Locale.forLanguageTag("pt-BR"), LocaleHelper.getLocale(contextWith("pt-BR")))
        assertEquals(Locale.forLanguageTag("pt"), LocaleHelper.getLocale(contextWith("pt-PT")))
    }

    @Test
    fun setLocale_brazilianPortuguese_setsApplicationLocales() {
        LocaleHelper.setLocale(application, Locale.forLanguageTag("pt-BR"))

        assertEquals("pt-BR", frameworkAppLocales())
    }

    @Test
    fun resetLocale_appLocalesAndStoredTagSet_clearsBoth() {
        localeManager().applicationLocales = LocaleList.forLanguageTags("ru")
        setStoredTag("ru")

        LocaleHelper.resetLocale(application)

        assertEquals("", frameworkAppLocales())
        assertFalse(preferences.contains(SELECTED_LANGUAGE))
    }

    @Test
    @Config(sdk = [32])
    fun onAttach_sdk32PersianRequested_returnsRtlPersianContext() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))

        val configuration = LocaleHelper.onAttach(application).resources.configuration

        assertEquals("fa", configuration.locales[0].language)
        assertEquals(LayoutDirection.RTL, configuration.layoutDirection)
        assertEquals(Locale.forLanguageTag("fa"), LocaleHelper.getLocale(application))
    }

    @Test
    fun onAttach_sdk36_returnsSameContext() {
        localeManager().applicationLocales = LocaleList.forLanguageTags("ru")

        assertTrue(LocaleHelper.onAttach(application) === application)
    }

    @Test
    fun onAttach_sdk36UnsupportedFirstSystemLocale_defaultsToFirstSupported() {
        LocaleHelper.onAttach(contextWith("it-IT,ru-RU"))

        assertEquals(Locale.forLanguageTag("ru-RU"), Locale.getDefault())
    }

    @Test
    fun onAttach_sdk36PlatformDefaultsToSecondLocale_putsItFirstInDefaultList() {
        // Mirrors ResourcesManager: it picks the first supported locale but keeps the list order.
        LocaleList::class.java.getDeclaredMethod("setDefault", LocaleList::class.java, Int::class.java)
            .invoke(null, LocaleList.forLanguageTags("it-IT,ru-RU"), 1)

        LocaleHelper.onAttach(contextWith("it-IT,ru-RU"))

        assertEquals(Locale.forLanguageTag("ru-RU"), LocaleList.getDefault()[0])
    }

    @Test
    fun onAttach_sdk36SupportedRegionalSystemLocale_keepsRegionInDefault() {
        LocaleHelper.onAttach(contextWith("de-CH"))

        assertEquals(Locale.forLanguageTag("de-CH"), Locale.getDefault())
    }

    @Test
    fun localesConfig_declaredLocales_matchLocaleType() {
        val declared = mutableSetOf<String>()
        application.resources.getXml(R.xml.locales_config).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    declared += parser.getAttributeValue(ANDROID_NAMESPACE, "name")
                }
            }
        }

        assertEquals(LocaleType.entries.map { it.tag }.toSet(), declared)
    }

    private fun setStoredTag(tag: String) {
        preferences.edit(commit = true) { putString(SELECTED_LANGUAGE, tag) }
    }

    private fun localeManager(): LocaleManager = application.getSystemService(LocaleManager::class.java)

    private fun frameworkAppLocales(): String = localeManager().applicationLocales.toLanguageTags()

    private fun contextWith(localeTags: String): Context {
        val configuration = Configuration(application.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(localeTags))
        return application.createConfigurationContext(configuration)
    }

    private companion object {
        const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
