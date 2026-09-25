package cash.p.terminal.modules.main

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.ScreenSecurityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class Nav3HostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val navigation = HSNavigation(NavBackStack<HSPage>(PlainTestPage()))
    private val isLocked = mutableStateOf(false)

    private val isFlagSecure: Boolean
        get() = composeRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Before
    fun setUp() {
        ScreenSecurityState.isAppLocked = false
        composeRule.setContent { Nav3Host(navigation, isLocked = isLocked) }
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        ScreenSecurityState.isAppLocked = false
    }

    @Test
    fun screenshotFlagAction_allInputs_matchesTable() {
        assertEquals(ScreenshotFlagAction.Add, screenshotFlagAction(anySecureEntryComposed = true, appLocked = false))
        assertEquals(ScreenshotFlagAction.Keep, screenshotFlagAction(anySecureEntryComposed = true, appLocked = true))
        assertEquals(ScreenshotFlagAction.Keep, screenshotFlagAction(anySecureEntryComposed = false, appLocked = true))
        assertEquals(
            ScreenshotFlagAction.Clear, screenshotFlagAction(anySecureEntryComposed = false, appLocked = false)
        )
    }

    @Test
    fun nav3Host_plainRoot_clearsFlagSecure() {
        assertFalse(isFlagSecure)
    }

    @Test
    fun nav3Host_securePagePushed_setsFlagSecure() {
        navigate { slideFromRight(SecureTestPage()) }

        assertTrue(isFlagSecure)
    }

    @Test
    fun nav3Host_sheetOverSecurePage_keepsFlagSecure() {
        navigate { slideFromRight(SecureTestPage()) }
        navigate { slideFromBottom(TestSheet()) }

        assertTrue(isFlagSecure)
    }

    @Test
    fun nav3Host_backFromSecurePage_keepsFlagSecureUntilExitTransitionEnds() {
        val securePage = SecureTestPage()
        navigate { slideFromRight(securePage) }
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { navigation.navigateUp() }
        composeRule.mainClock.advanceTimeBy(100)
        assertTrue(isFlagSecure)

        var frames = 0
        while (securePage.composed && frames++ < MAX_EXIT_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        assertFalse(isFlagSecure)
    }

    @Test
    fun nav3Host_hostPausedAndResumed_securesOnlyWhilePaused() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertTrue(isFlagSecure)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertFalse(isFlagSecure)
    }

    @Test
    fun nav3Host_appLockedOnResume_keepsFlagAsLockScreenSetIt() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        ScreenSecurityState.isAppLocked = true

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        assertTrue(isFlagSecure)
    }

    @Test
    fun nav3Host_unlockedOverPlainPage_clearsFlagSecureSetByLockScreen() {
        lock()

        unlock()

        assertFalse(isFlagSecure)
    }

    @Test
    fun nav3Host_unlockedOverSecurePage_restoresFlagSecure() {
        navigate { slideFromRight(SecureTestPage()) }
        lock()

        composeRule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        unlock()

        assertTrue(isFlagSecure)
    }

    @Test
    fun nav3Host_calculatorLockOverSecurePage_keepsDisguiseScreenshotable() {
        navigate { slideFromRight(SecureTestPage()) }
        composeRule.runOnUiThread {
            composeRule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            ScreenSecurityState.isAppLocked = true
            isLocked.value = true
        }
        composeRule.waitForIdle()

        assertFalse(isFlagSecure)
    }

    private fun lock() {
        composeRule.runOnUiThread {
            ScreenSecurityState.isAppLocked = true
            isLocked.value = true
            composeRule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        composeRule.waitForIdle()
    }

    private fun unlock() {
        composeRule.runOnUiThread {
            ScreenSecurityState.isAppLocked = false
            isLocked.value = false
        }
        composeRule.waitForIdle()
    }

    private fun navigate(action: HSNavigation.() -> Unit) {
        composeRule.runOnUiThread { navigation.action() }
        composeRule.waitForIdle()
    }

    private companion object {
        const val MAX_EXIT_FRAMES = 120
    }
}
