package cash.p.terminal.ui.compose.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import cash.p.terminal.RecreatableContent
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalAnimationApi::class)
class SearchBarDesktopTest {

    @Test
    fun searchMode_textFieldDoesNotOverlapBackButton() = runComposeUiTest {
        setContent {
            ComposeAppTheme { SearchBar(title = "Title", searchModeInitial = true, onClose = {}) }
        }

        val back = onNodeWithContentDescription("Back").getBoundsInRoot()
        val field = onNode(hasSetTextAction()).getBoundsInRoot()

        assertTrue(field.left >= back.right, "field $field overlaps back button $back")
    }

    @Test
    fun searchMode_backButtonClick_invokesOnClose() = runComposeUiTest {
        var closes = 0
        setContent {
            ComposeAppTheme {
                SearchBar(title = "Title", searchModeInitial = true, searchOnlyMode = true, onClose = { closes++ })
            }
        }

        onNodeWithContentDescription("Back").performClick()

        assertEquals(1, closes)
    }

    @Test
    fun searchMode_typingAndClear_reportsTextChanges() = runComposeUiTest {
        val texts = mutableListOf<String>()
        setContent {
            ComposeAppTheme {
                SearchBar(title = "Title", searchModeInitial = true, onClose = {}, onSearchTextChanged = { texts += it })
            }
        }

        onNode(hasSetTextAction()).performTextInput("btc")
        onNodeWithContentDescription("Cancel").assertIsDisplayed().performClick()

        assertEquals(listOf("btc", ""), texts)
    }

    @Test
    fun recreation_searchModeWithAQuery_restoresItAndRepeatsItToTheCaller() = runComposeUiTest {
        var alive by mutableStateOf(true)
        val texts = mutableListOf<String>()
        setContent {
            ComposeAppTheme {
                RecreatableContent(alive) {
                    SearchBar(title = "Title", onClose = {}, onSearchTextChanged = { texts += it })
                }
            }
        }
        onNodeWithContentDescription("Search").performClick()
        onNode(hasSetTextAction()).performTextInput("bitcoin")

        runOnIdle { alive = false }
        runOnIdle { alive = true }
        waitForIdle()

        onNodeWithText("bitcoin").assertIsDisplayed()
        onNodeWithContentDescription("Cancel").assertIsDisplayed()
        onNodeWithContentDescription("Search").assertDoesNotExist()
        // The caller's filter does not survive a recreation everywhere, so it hears the query again.
        assertEquals(listOf("bitcoin", "bitcoin"), texts)
    }
}
