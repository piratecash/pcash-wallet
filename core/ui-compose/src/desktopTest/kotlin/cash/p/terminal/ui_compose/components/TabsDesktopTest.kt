package cash.p.terminal.ui_compose.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TabsDesktopTest {

    private val tabs = listOf("Price", "Analytics", "Markets").mapIndexed { index, title ->
        TabItem(title = title, selected = index == 0, item = index)
    }
    private val rowHeight = 44.dp
    private val indicatorHeight = 2.dp
    private var indicatorColor = Color.Unspecified

    @Test
    fun tabs_selectedLabel_isCenteredAndAboveIndicator() = runComposeUiTest {
        setContent { ComposeAppTheme { indicatorColor = ComposeAppTheme.colors.brandDefault; Tabs(tabs = tabs, onClick = {}) } }

        assertLabelCenteredAboveBottomIndicator()
    }

    @Test
    fun scrollableTabs_selectedLabel_isCenteredAndAboveIndicator() = runComposeUiTest {
        setContent { ComposeAppTheme { indicatorColor = ComposeAppTheme.colors.brandDefault; ScrollableTabs(tabs = tabs, onClick = {}) } }

        assertLabelCenteredAboveBottomIndicator()
    }

    private fun ComposeUiTest.assertLabelCenteredAboveBottomIndicator() {
        val label = onNodeWithText("Price", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(rowHeight, onRoot().getBoundsInRoot().height)

        val labelCenter = (label.top + label.bottom) / 2
        assertTrue(abs((labelCenter - rowHeight / 2).value) <= 1f, "label centre $labelCenter")
        assertTrue(label.bottom <= rowHeight - indicatorHeight, "label bottom ${label.bottom} overlaps indicator")

        val (first, last) = onRoot().captureToImage().indicatorRows(indicatorColor)
        val scale = density.density
        assertEquals(((rowHeight - indicatorHeight).value * scale).toInt(), first, "indicator start")
        assertEquals((rowHeight.value * scale).toInt(), last, "indicator end")
    }

    private fun ImageBitmap.indicatorRows(color: Color): Pair<Int, Int> {
        val pixels = toPixelMap()
        val rows = (0 until height).filter { y ->
            (0 until width).any { x -> pixels[x, y].isCloseTo(color) }
        }
        assertTrue(rows.isNotEmpty(), "indicator not drawn")
        return rows.first() to rows.last() + 1
    }

    private fun Color.isCloseTo(other: Color) =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f
}
