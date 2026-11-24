package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HsSettingCellDesktopTest {

    private var rowClicks = 0
    private var infoClicks = 0

    @Test
    fun hsSettingCell_infoIconClicked_callsInfoClickOnly() = runComposeUiTest {
        setContent { Cell(withInfo = true) }

        onNodeWithContentDescription(INFO).performClick()

        assertEquals(1, infoClicks)
        assertEquals(0, rowClicks)
    }

    @Test
    fun hsSettingCell_rowClicked_callsRowClickOnly() = runComposeUiTest {
        setContent { Cell(withInfo = true) }

        onNodeWithText(TITLE).performClick()

        assertEquals(1, rowClicks)
        assertEquals(0, infoClicks)
    }

    @Test
    fun hsSettingCell_noInfoClick_rendersNoInfoIcon() = runComposeUiTest {
        setContent { Cell(withInfo = false) }

        onNodeWithText(TITLE).assertExists()
        onNodeWithContentDescription(INFO).assertDoesNotExist()
    }

    @Composable
    private fun Cell(withInfo: Boolean) {
        ComposeAppTheme {
            HsSettingCell(
                title = TITLE,
                arrowPainter = null,
                onClick = { rowClicks++ },
                onInfoClick = if (withInfo) ({ infoClicks++ }) else null,
            )
        }
    }

    private companion object {
        const val TITLE = "Address search"
        const val INFO = "Info"
    }
}
