package cash.p.terminal.screenshots

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import cash.p.terminal.modules.settings.main.MainSettingsViewModel
import cash.p.terminal.modules.settings.main.SettingsScreen
import cash.p.terminal.modules.settings.main.settingsContentTestState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    private val navController = mockk<NavController>(relaxed = true)

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

    private fun capture(name: String, darkTheme: Boolean) {
        val outputDirectory = System.getenv("MOBILE812_SNAPSHOT_DIR")
            ?: "build/tmp/mobile812/current"
        captureRoboImage(filePath = "$outputDirectory/$name.png") {
            ComposeAppTheme(darkTheme = darkTheme) {
                SettingsScreen(
                    fragmentNavController = navController,
                    paddingValues = PaddingValues(),
                    viewModel = viewModel,
                )
            }
        }
    }
}
