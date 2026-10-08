package cash.p.terminal.screenshots

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.R
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.settings.main.MainSettingsViewModel
import cash.p.terminal.modules.settings.main.SettingsScreen
import cash.p.terminal.modules.settings.main.settingsContentTestState
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.LocalHostLifecycleOwner
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.every
import io.mockk.mockk
import io.horizontalsystems.core.IPinComponent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = "en-w393dp-h2400dp-xxhdpi",
)
class SettingsScreenScreenshotTest {

    private val viewModel = mockk<MainSettingsViewModel>(relaxed = true) {
        every { uiState } returns settingsContentTestState
        every { appVersion } returns "1.2.3"
        every { companyWebPage } returns "https://example.com"
    }
    private val navigation = HSNavigation(NavBackStack<HSPage>(PlainTestPage()))

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { mockk<AppPages>() }
                    single { mockk<IPinComponent>(relaxed = true) }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun settingsScreen_lightEnglish_capturesSnapshot() = capture("light-en", darkTheme = false)

    @Test
    fun settingsScreen_darkEnglish_capturesSnapshot() = capture("dark-en", darkTheme = true)

    @Test
    @Config(qualifiers = "ru-w393dp-h2400dp-xxhdpi")
    fun settingsScreen_lightRussian_capturesSnapshot() = capture("light-ru", darkTheme = false)

    @Test
    @Config(qualifiers = "ar-w393dp-h2400dp-xxhdpi")
    fun settingsScreen_lightArabicRtl_capturesSnapshot() = capture("light-ar-rtl", darkTheme = false)

    @Test
    fun alertIcon_dayTheme_usesLightTint() {
        assertEquals(0xFFFF3D43.toInt(), alertIconTint())
    }

    @Test
    @Config(qualifiers = "en-w393dp-h2400dp-night-xxhdpi")
    fun alertIcon_nightTheme_usesDarkTint() {
        assertEquals(0xFFF43A4F.toInt(), alertIconTint())
    }

    private fun capture(name: String, darkTheme: Boolean) {
        val outputDirectory = System.getenv("MOBILE812_SNAPSHOT_DIR")
            ?: "build/tmp/mobile812/current"
        captureRoboImage(filePath = "$outputDirectory/$name.png") {
            CompositionLocalProvider(LocalHostLifecycleOwner provides LocalLifecycleOwner.current) {
                ComposeAppTheme(darkTheme = darkTheme) {
                    SettingsScreen(
                        navigation = navigation,
                        paddingValues = PaddingValues(),
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    private fun alertIconTint(): Int {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val parser = context.resources.getXml(R.drawable.ic_attention_red_20)
        while (parser.eventType != XmlPullParser.START_TAG) {
            parser.next()
        }
        val tint = parser.getAttributeResourceValue(
            "http://schemas.android.com/apk/res/android",
            "tint",
            0,
        )
        parser.close()
        assertEquals(R.color.lucian, tint)
        return context.getColor(tint)
    }
}
