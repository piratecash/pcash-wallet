package cash.p.terminal.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavBackStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SafeNavigationInNavDisplayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val root = RootPage()
    private val backStack = NavBackStack<HSPage>(root)
    private val navigation = HSNavigation(backStack)

    @Before
    fun setUp() {
        BackNavigationGate.release()
        composeRule.setContent { TestNavDisplay(navigation) }
        composeRule.waitForIdle()
    }

    @Test
    fun navigateUpSafely_calledByPageAnimatingOut_keepsPageBeneath() {
        val send = SendPage()
        val closing = SelfClosingPage()
        push(send, PinPage(), closing)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { navigation.removeLastUntil(PinPage::class, inclusive = true) }
        composeRule.mainClock.advanceTimeBy(EXIT_MIDPOINT_MS)
        composeRule.waitForIdle()
        assertTrue(closing.composed)
        composeRule.runOnIdle { closing.closeRequested = true }
        var frames = 0
        while (closing.composed && frames++ < MAX_EXIT_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(listOf(root, send), backStack.toList())
    }

    @Test
    fun navigateUpSafely_calledBySheetOnTop_closesSheet() {
        val send = SendPage()
        val sheet = SelfClosingPage(bottomSheet = true)
        push(send, sheet)
        assertEquals(Lifecycle.State.RESUMED, sheet.entryLifecycle?.currentState)

        composeRule.runOnIdle { sheet.closeRequested = true }
        composeRule.waitForIdle()

        assertEquals(listOf(root, send), backStack.toList())
    }

    @Test
    fun navigateUpSafely_calledBySettledTopPage_closesPage() {
        val page = SelfClosingPage()
        push(page)

        composeRule.runOnIdle { page.closeRequested = true }
        composeRule.waitForIdle()

        assertEquals(listOf(root), backStack.toList())
    }

    @Test
    fun navigateUpFrom_resultCallbackAfterFullScreenCalleeCloses_closesCaller() {
        val caller = SendPage()
        val callee = PinPage()
        push(caller)
        composeRule.runOnIdle {
            navigation.slideFromRightForResult<String>(callee) { navigation.navigateUpFrom(caller) }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            navigation.setResult(callee, "done")
            navigation.navigateUpSafely()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(root), backStack.toList())
    }

    @Test
    fun removeLastUntil_resultCallbackFromSheet_closesSheetAndCaller() {
        val sheet = RiskyAddressSheet()
        push(SendPage())
        composeRule.runOnIdle {
            navigation.slideFromBottomForResult<String>(sheet) {
                navigation.removeLastUntil(SendPage::class, inclusive = true)
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            navigation.setResult(sheet, "cancel")
            navigation.navigateUpSafely()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(root), backStack.toList())
    }

    private fun push(vararg pages: HSPage) {
        pages.forEach { page ->
            composeRule.runOnIdle {
                if (page.bottomSheet) navigation.slideFromBottom(page) else navigation.slideFromRight(page)
            }
            composeRule.waitForIdle()
        }
    }

    private companion object {
        const val EXIT_MIDPOINT_MS = 100L
        const val MAX_EXIT_FRAMES = 120
    }
}
