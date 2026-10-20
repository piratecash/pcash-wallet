package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

// Any root works: the scene routes injected input the same way whichever of its roots sends it.
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.clickAt(offset: Offset) {
    onAllNodes(isRoot()).onFirst().performTouchInput { click(offset) }
    waitForIdle()
}

/** Walks through the first-run intro slides to the create password screen. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.finishIntro() = repeat(INTRO_SLIDES) {
    onNodeWithText(NEXT).performClick()
    waitForIdle()
}

private const val INTRO_SLIDES = 3
private const val NEXT = "Next"

/** A back stack needs a root page even where the test composes the app itself. */
internal class BlankPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) = Unit
}
