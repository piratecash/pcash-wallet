package cash.p.terminal.shared.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalTestApi::class)
class ScreenCaptureProtectionDesktopTest {

    private val navigation = HSNavigation(NavBackStack<HSPage>(PlainPage()))
    private lateinit var protection: ScreenCaptureProtection

    @BeforeTest
    fun setUp() {
        DesktopTestGraph.start()
        protection = GlobalContext.get().get()
    }

    @AfterTest
    fun tearDown() = DesktopTestGraph.stop()

    @Test
    fun register_twoTokens_protectedUntilBothAreGone() {
        val first = Any()
        val second = Any()

        protection.register(first)
        protection.register(second)
        protection.unregister(first)
        val afterFirstGone = protection.isProtected.value
        protection.unregister(second)

        assertEquals(true to false, afterFirstGone to protection.isProtected.value)
    }

    @Test
    fun pcashApp_securePagePushedThenPopped_protectsOnlyWhileShown() = runDesktopComposeUiTest {
        setContent { Host() }
        runOnIdle { assertFalse(protection.isProtected.value) }

        runOnIdle { navigation.slideFromRight(SecurePage()) }
        runOnIdle { assertTrue(protection.isProtected.value) }

        runOnIdle { navigation.navigateUp() }
        runOnIdle { assertFalse(protection.isProtected.value) }
    }

    @Test
    fun pcashApp_secureBottomSheet_protects() = runDesktopComposeUiTest {
        setContent { Host() }

        runOnIdle { navigation.slideFromBottom(SecureSheet()) }

        runOnIdle { assertTrue(protection.isProtected.value) }
    }

    @Composable
    private fun Host() {
        TestWindowHost {
            ComposeAppTheme {
                PcashApp(navigation)
            }
        }
    }

    private class PlainPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = body_leah(text = "plain")
    }

    private class SecurePage : HSPage(screenshotEnabled = false) {
        @Composable
        override fun GetContent(navigation: HSNavigation) = body_leah(text = "secure")
    }

    private class SecureSheet : HSPage(bottomSheet = true, screenshotEnabled = false) {
        @Composable
        override fun GetContent(navigation: HSNavigation) = body_leah(text = "sheet")
    }
}
