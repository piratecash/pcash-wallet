package cash.p.terminal.ui_compose.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.theme.lightPalette
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppSelectorDialogDesktopTest {

    private val items = listOf(
        AppSelectorItem("First", selected = true, item = 1),
        AppSelectorItem("Second", selected = false, item = 2),
    )

    @Test
    fun row_anyItem_isAtLeastTouchTargetHeight() = runComposeUiTest {
        showDialog()

        onNodeWithText("First").assertHeightIsAtLeast(48.dp)
        onNodeWithText("Second").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun row_selectedItem_usesBrandColorOthersUseTextPrimary() = runComposeUiTest {
        showDialog()

        assertEquals(lightPalette.brandDefault, onNodeWithText("First").textColor())
        assertEquals(lightPalette.textPrimary, onNodeWithText("Second").textColor())
    }

    @Test
    fun row_otherItemClicked_reportsSelectionThenDismisses() = runComposeUiTest {
        val events = mutableListOf<String>()
        showDialog(events)

        onNodeWithText("Second").performClick()

        assertEquals(listOf("select 2", "dismiss"), events)
    }

    @Test
    fun row_selectedItemClicked_onlyDismisses() = runComposeUiTest {
        val events = mutableListOf<String>()
        showDialog(events)

        onNodeWithText("First").performClick()

        assertEquals(listOf("dismiss"), events)
    }

    private fun ComposeUiTest.showDialog(events: MutableList<String> = mutableListOf()) {
        setContent {
            ComposeAppTheme(darkTheme = false) {
                AppSelectorDialog(
                    title = "Title",
                    items = items,
                    onSelect = { events += "select $it" },
                    onDismiss = { events += "dismiss" },
                )
            }
        }
        waitForIdle()
    }

    private fun SemanticsNodeInteraction.textColor() = fetchSemanticsNode().config
        .getOrNull(SemanticsActions.GetTextLayoutResult)
        ?.action
        ?.let { getLayout ->
            val results = mutableListOf<TextLayoutResult>()
            getLayout(results)
            results.single().layoutInput.style.color
        }
}
