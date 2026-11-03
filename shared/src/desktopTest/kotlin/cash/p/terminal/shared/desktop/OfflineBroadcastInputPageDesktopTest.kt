package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.send.offline.OfflineBroadcastInputPage
import cash.p.terminal.modules.send.offline.OfflineBroadcastPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class OfflineBroadcastInputPageDesktopTest {

    private val settingsPage = SettingsStubPage()
    private val page = OfflineBroadcastInputPage()
    private val navigation = HSNavigation(NavBackStack<HSPage>(settingsPage, page))

    @BeforeTest
    fun setUp() = DesktopTestGraph.start()

    @AfterTest
    fun tearDown() = DesktopTestGraph.stop()

    @Test
    fun continueButton_blankInput_isDisabled() = runDesktopComposeUiTest {
        showPage()

        onNode(hasSetTextAction()).performTextInput("  ")

        onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun continueButton_pastedTransaction_opensBroadcastPageWithIt() = runDesktopComposeUiTest {
        showPage()

        onNode(hasSetTextAction()).performTextInput(RAW_CONTENT)
        onNodeWithText("Continue").assertIsEnabled().performClick()

        val broadcast = assertIs<OfflineBroadcastPage>(navigation.backStack.last())
        assertEquals(RAW_CONTENT, broadcast.input?.initialInput)
    }

    @Test
    fun continueButton_pastedTransaction_replacesInputPageLikeAndroidScanner() = runDesktopComposeUiTest {
        showPage()

        onNode(hasSetTextAction()).performTextInput(RAW_CONTENT)
        onNodeWithText("Continue").performClick()

        assertEquals(listOf(settingsPage, navigation.backStack.last()), navigation.backStack.toList())
    }

    @Test
    fun continueButton_doubleClick_opensOneBroadcastPage() = runDesktopComposeUiTest {
        showPage()
        onNode(hasSetTextAction()).performTextInput(RAW_CONTENT)

        onNodeWithText("Continue").performClick().performClick()

        assertEquals(2, navigation.backStack.size)
    }

    @Test
    fun continueButton_afterLockUnlockRecreation_submitsTheVisibleText() = runDesktopComposeUiTest {
        var alive by mutableStateOf(true)
        setContent {
            // Same saveable-state round trip as DesktopRoot's lock/unlock.
            val holder = rememberSaveableStateHolder()
            if (alive) holder.SaveableStateProvider(PAGE_KEY) { ComposeAppTheme { page.GetContent(navigation) } }
        }
        onNode(hasSetTextAction()).performTextInput(RAW_CONTENT)

        runOnIdle { alive = false }
        runOnIdle { alive = true }

        onNode(hasSetTextAction()).assertTextEquals(RAW_CONTENT)
        onNodeWithText("Continue").performClick()
        val broadcast = assertIs<OfflineBroadcastPage>(navigation.backStack.last())
        assertEquals(RAW_CONTENT, broadcast.input?.initialInput)
    }

    private fun ComposeUiTest.showPage() {
        setContent { ComposeAppTheme { page.GetContent(navigation) } }
    }

    private class SettingsStubPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }

    private companion object {
        const val RAW_CONTENT = "deadbeefdeadbeefdead"
        const val PAGE_KEY = "page"
    }
}
