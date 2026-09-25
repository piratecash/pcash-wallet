package cash.p.terminal.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PageResumeEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val host = TestHostLifecycleOwner()
    private val page = ResumeTrackingPage()
    private val backStack = NavBackStack<HSPage>(page)
    private val navigation = HSNavigation(backStack)

    @Before
    fun setUp() {
        composeRule.setContent {
            CompositionLocalProvider(LocalHostLifecycleOwner provides host) {
                TestNavDisplay(navigation)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun pageResumeEffect_sheetOpenedOverPage_staysActive() {
        composeRule.runOnIdle { navigation.slideFromBottom(TestSheet()) }
        composeRule.waitForIdle()

        // Nav3 caps an entry under an overlay at STARTED; the effect must still count it resumed.
        assertEquals(Lifecycle.State.STARTED, page.entryLifecycle?.currentState)
        assertEquals(listOf("resume"), page.events)

        composeRule.runOnIdle { navigation.navigateUp() }
        composeRule.waitForIdle()
        assertEquals(listOf("resume"), page.events)
    }

    @Test
    fun pageResumeEffect_coveredByPage_pausesAndResumesOnReturn() {
        composeRule.runOnIdle { navigation.slideFromRight(TestPage()) }
        composeRule.waitForIdle()
        assertEquals(listOf("resume", "pause"), page.events)

        composeRule.runOnIdle { navigation.navigateUp() }
        composeRule.waitForIdle()
        assertEquals(listOf("resume", "pause", "resume"), page.events)
    }

    @Test
    fun pageResumeEffect_hostPaused_pausesAndResumesWithHost() {
        composeRule.runOnIdle { host.lifecycle.currentState = Lifecycle.State.STARTED }
        composeRule.waitForIdle()
        assertEquals(listOf("resume", "pause"), page.events)

        composeRule.runOnIdle { host.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        assertEquals(listOf("resume", "pause", "resume"), page.events)
    }

    private class ResumeTrackingPage : HSPage() {
        val events = mutableListOf<String>()
        var entryLifecycle: Lifecycle? = null

        @Composable
        override fun GetContent(navigation: HSNavigation) {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            SideEffect { entryLifecycle = lifecycle }
            PageResumeEffect(onResume = { events += "resume" }, onPause = { events += "pause" })
        }
    }

    private class TestHostLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }
}
