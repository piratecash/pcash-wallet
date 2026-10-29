package cash.p.terminal.shared.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import cash.p.terminal.modules.settings.privacy.PrivacyScreen
import cash.p.terminal.resources.Privacy_BulletedText1
import cash.p.terminal.resources.Privacy_BulletedText3
import cash.p.terminal.resources.Privacy_Information
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ShareCrashData
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PrivacyScreenDesktopTest {

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    @Test
    fun privacyScreen_noCrashCollection_hidesIntroThirdBulletAndToggle() = runComposeUiTest {
        setContent { ComposeAppTheme { PrivacyScreen(shareCrashData = null, onToggleShareCrashData = {}, onClose = {}) } }

        onNodeWithText(text(Res.string.Privacy_BulletedText1)).assertExists()
        onNodeWithText(text(Res.string.Privacy_Information)).assertDoesNotExist()
        onNodeWithText(text(Res.string.Privacy_BulletedText3)).assertDoesNotExist()
        onNodeWithText(text(Res.string.ShareCrashData)).assertDoesNotExist()
    }

    @Test
    fun privacyScreen_crashCollection_showsIntroThirdBulletAndToggle() = runComposeUiTest {
        setContent {
            ComposeAppTheme { PrivacyScreen(shareCrashData = true, onToggleShareCrashData = {}, onClose = {}) }
        }

        onNodeWithText(text(Res.string.Privacy_Information)).assertExists()
        onNodeWithText(text(Res.string.Privacy_BulletedText3)).assertExists()
        onNodeWithText(text(Res.string.ShareCrashData)).assertExists()
    }
}
