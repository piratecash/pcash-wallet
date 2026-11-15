package cash.p.terminal.modules.nft.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NftCardsRowDesktopTest {

    @Test
    fun snapShowAll_dragInterruptsSnap_laterStopStillSnaps() = runDesktopComposeUiTest {
        setContent {
            ComposeAppTheme {
                NftCardsRow(
                    items = previewNftItems(11),
                    hidden = false,
                    onItemClick = {},
                    onShowAll = {},
                    modifier = Modifier.width(400.dp),
                )
            }
        }
        onRoot().performTouchInput { slowDrag(-3000f) }
        waitForIdle()
        val revealedLeft = showAllLeft()

        mainClock.autoAdvance = false
        onRoot().performTouchInput { slowDrag(PARTIAL_DRAG) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(SNAP_STARTED_MS)
        onRoot().performTouchInput { slowDrag(PARTIAL_DRAG) }
        mainClock.autoAdvance = true
        waitForIdle()

        onRoot().performTouchInput { slowDrag(PARTIAL_DRAG) }
        waitForIdle()

        assertEquals(revealedLeft, showAllLeft())
    }

    private fun ComposeUiTest.showAllLeft() = onNodeWithText("Show all").getBoundsInRoot().left

    // Holding still before release kills the velocity, so the row stops where the finger stopped.
    private fun TouchInjectionScope.slowDrag(dx: Float) {
        down(center)
        repeat(DRAG_STEPS) { moveBy(Offset(dx / DRAG_STEPS, 0f)) }
        advanceEventTime(500)
        up()
    }

    private companion object {
        const val DRAG_STEPS = 20
        const val PARTIAL_DRAG = 50f
        const val SNAP_STARTED_MS = 32L
    }
}
