package cash.p.terminal.ui.compose.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
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
}
