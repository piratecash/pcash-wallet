package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.theme.darkPalette
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BottomSheetSurfaceDesktopTest {

    @Test
    fun cellSection_insideBottomSheetSurface_isTransparentWithBorderDefaultOutline() = runComposeUiTest {
        setContent {
            ComposeAppTheme(darkTheme = true) {
                BottomSheetSurface { SectionWithProbe() }
            }
        }

        val (edge, center) = probePixels()

        assertEquals(darkPalette.borderDefault, edge)
        assertEquals(darkPalette.surfaceElevated, center)
    }

    @Test
    fun cellSection_outsideBottomSheetSurface_isFilledWithSurfacePrimary() = runComposeUiTest {
        setContent {
            ComposeAppTheme(darkTheme = true) { SectionWithProbe() }
        }

        val (edge, center) = probePixels()

        assertEquals(darkPalette.surfacePrimary, edge)
        assertEquals(darkPalette.surfacePrimary, center)
    }

    @Composable
    private fun SectionWithProbe() {
        CellUniversalLawrenceSection {
            Box(Modifier.size(100.dp, 40.dp).testTag(PROBE_TAG))
        }
    }

    private fun ComposeUiTest.probePixels(): Pair<Color, Color> {
        waitForIdle()
        val pixels = onNodeWithTag(PROBE_TAG).captureToImage().toPixelMap()
        return pixels[0, pixels.height / 2] to pixels[pixels.width / 2, pixels.height / 2]
    }

    private companion object {
        const val PROBE_TAG = "probe"
    }
}
