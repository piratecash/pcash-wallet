package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    private companion object {
        const val LIST_TAG = "list"
    }
}
