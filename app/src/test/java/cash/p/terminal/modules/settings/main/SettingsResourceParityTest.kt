package cash.p.terminal.modules.settings.main

import android.app.Application
import android.content.Context
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.R
import cash.p.terminal.shared.settings.SettingsContent
import cash.p.terminal.strings.helpers.LocaleHelper
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsResourceParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        LocaleHelper.setLocale(application, originalLocale)
    }

    @Test
    fun settingsContent_allBundledLocales_matchesAndroidResources() {
        val localeTag = mutableStateOf("en")
        setContent { localeTag.value }
        var portugueseContact = ""
        var brazilianPortugueseContact = ""

        localeTags.forEach { tag ->
            lateinit var expected: List<String>
            compose.runOnIdle {
                val context = setLocale(tag)
                expected = parityResourceIds.map { context.getString(it) }
                localeTag.value = tag
            }
            expected.forEach { text ->
                compose.onNodeWithText(text).performScrollTo().assertExists()
            }
            when (tag) {
                "pt" -> portugueseContact = expected.last()
                "pt-BR" -> brazilianPortugueseContact = expected.last()
                "uk" -> assertFalse(expected.last().contains('\\'))
            }
        }

        assertNotEquals(portugueseContact, brazilianPortugueseContact)
    }

    @Test
    fun settingsContent_runtimeLocaleChange_updatesFrenchEscapedStrings() {
        val localeTag = mutableStateOf("en")
        setLocale("en")
        setContent { localeTag.value }

        compose.onNodeWithText("About App").performScrollTo().assertExists()
        compose.onNodeWithText("Address Checker").performScrollTo().assertExists()

        lateinit var frenchAbout: String
        lateinit var frenchAddressChecker: String
        compose.runOnIdle {
            val context = setLocale("fr")
            frenchAbout = context.getString(R.string.SettingsAboutApp_Title)
            frenchAddressChecker = context.getString(R.string.address_checker_title)
            localeTag.value = "fr"
        }

        compose.onNodeWithText(frenchAbout).performScrollTo().assertExists()
        compose.onNodeWithText(frenchAddressChecker).performScrollTo().assertExists()
        assertFalse(frenchAbout.contains('\\'))
        assertFalse(frenchAddressChecker.contains('\\'))
    }

    private fun setContent(localeTag: () -> String) {
        compose.setContent {
            ComposeAppTheme {
                key(localeTag()) {
                    SettingsContent(
                        uiState = settingsContentTestState,
                        appVersion = "1.2.3",
                        onAction = {},
                        alertPainter = ColorPainter(Color.Red),
                    )
                }
            }
        }
    }

    private fun setLocale(tag: String): Context {
        LocaleHelper.setLocale(application, Locale.forLanguageTag(tag))
        return LocaleHelper.onAttach(application)
    }

    private companion object {
        val localeTags = listOf(
            "en", "ar", "de", "es", "fa", "fr", "ko", "nl", "pt", "pt-BR", "ru", "tr",
            "uk", "zh",
        )
        val parityResourceIds = listOf(
            R.string.SettingsAboutApp_Title,
            R.string.address_checker_title,
            R.string.SettingsContact_Title,
        )
    }
}
