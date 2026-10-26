package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HSSwipeRefreshDesktopTest {

    @Test
    fun content_isShown() = runComposeUiTest {
        setContent {
            ComposeAppTheme {
                HSSwipeRefresh(refreshing = false, onRefresh = {}) { Text("Body") }
            }
        }

        onNodeWithText("Body").assertExists()
    }

    @Test
    fun pullDown_onScrolledToTopContent_invokesOnRefresh() = runComposeUiTest {
        var refreshes = 0
        setContent {
            ComposeAppTheme {
                HSSwipeRefresh(refreshing = false, onRefresh = { refreshes++ }) {
                    Box(
                        Modifier
                            .testTag(LIST_TAG)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(Modifier.fillMaxWidth().height(2000.dp))
                    }
                }
            }
        }

        onNodeWithTag(LIST_TAG).performTouchInput { swipeDown(durationMillis = 400) }
        waitForIdle()

        assertTrue(refreshes >= 1, "onRefresh was not invoked, count=$refreshes")
    }

    @Test
    fun mouseScrollUp_pastThresholdOnTopContent_invokesOnRefreshOnceScrollStops() = runComposeUiTest {
        var refreshes = 0
        setScrollableContent { refreshes++ }

        onNodeWithTag(LIST_TAG).performMouseInput { repeat(10) { scroll(-1f) } }
        waitForScrollEnd()

        assertEquals(1, refreshes)
    }

    @Test
    fun mouseScrollUp_momentumCarriedContentToTop_doesNotRefresh() = runComposeUiTest {
        var refreshes = 0
        setScrollableContent { refreshes++ }
        onNodeWithTag(LIST_TAG).performMouseInput { scroll(10f) }
        waitForScrollEnd()
        mainClock.autoAdvance = false

        repeat(40) {
            onNodeWithTag(LIST_TAG).performMouseInput { scroll(-1f) }
            mainClock.advanceTimeBy(NOTCH_GAP_MS)
        }
        waitForScrollEnd()

        assertEquals(0, refreshes)
    }

    @Test
    fun mouseScrollUp_singleEventCrossingTop_doesNotRefresh() = runComposeUiTest {
        var refreshes = 0
        setScrollableContent { refreshes++ }
        onNodeWithTag(LIST_TAG).performMouseInput { scroll(10f) }
        waitForScrollEnd()

        onNodeWithTag(LIST_TAG).performMouseInput { scroll(-30f) }
        waitForScrollEnd()

        assertEquals(0, refreshes)
    }

    @Test
    fun refreshingFlag_doesNotInvokeOnRefresh() = runComposeUiTest {
        var refreshes = 0
        setContent {
            ComposeAppTheme {
                HSSwipeRefresh(refreshing = true, onRefresh = { refreshes++ }) { Text("Body") }
            }
        }
        waitForIdle()

        assertEquals(0, refreshes)
    }

    private fun ComposeUiTest.setScrollableContent(onRefresh: () -> Unit) = setContent {
        ComposeAppTheme {
            HSSwipeRefresh(refreshing = false, onRefresh = onRefresh) {
                Box(
                    Modifier
                        .testTag(LIST_TAG)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(Modifier.fillMaxWidth().height(2000.dp))
                }
            }
        }
    }

    private fun ComposeUiTest.waitForScrollEnd() {
        mainClock.advanceTimeBy(SCROLL_END_MS)
        waitForIdle()
    }

    private companion object {
        const val LIST_TAG = "list"
        const val SCROLL_END_MS = 1_000L

        // Outlasts the wheel's own scroll animation so later notches reach the top unconsumed.
        const val NOTCH_GAP_MS = 100L
    }
}
