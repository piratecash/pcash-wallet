package io.horizontalsystems.chartview

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.models.ChartPoint
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ChartMinimalDesktopTest {

    @Test
    fun chartMinimal_risingData_drawsStatusSuccessColor() = runComposeUiTest {
        var expected = Color.Unspecified
        setContent {
            ComposeAppTheme(darkTheme = true) {
                expected = ComposeAppTheme.colors.statusSuccess
                ChartMinimal(chartData(first = 1f, last = 3f), Modifier.size().testTag(TAG))
            }
        }
        waitForIdle()

        assertColorEquals(expected, curveColorAtMiddle(onNodeWithTag(TAG).captureToImage()))
    }

    @Test
    fun chartMinimal_fallingData_drawsStatusErrorColor() = runComposeUiTest {
        var expected = Color.Unspecified
        setContent {
            ComposeAppTheme(darkTheme = true) {
                expected = ComposeAppTheme.colors.statusError
                ChartMinimal(chartData(first = 3f, last = 1f), Modifier.size().testTag(TAG))
            }
        }
        waitForIdle()

        assertColorEquals(expected, curveColorAtMiddle(onNodeWithTag(TAG).captureToImage()))
    }

    private fun assertColorEquals(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < TOLERANCE &&
            abs(expected.green - actual.green) < TOLERANCE &&
            abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue(close, "expected=$expected actual=$actual")
    }

    private fun Modifier.size() = width(100.dp).height(60.dp)

    private fun chartData(first: Float, last: Float) = ChartData(
        items = listOf(ChartPoint(first, 1000L), ChartPoint(last, 2000L)),
        isMovementChart = true,
    )

    // Background is transparent, so the most opaque pixel in the middle column is the curve.
    private fun curveColorAtMiddle(image: ImageBitmap): Color {
        val pixels = image.toPixelMap()
        val x = pixels.width / 2
        return (0 until pixels.height).map { pixels[x, it] }.maxBy { it.alpha }.copy(alpha = 1f)
    }

    private companion object {
        const val TAG = "chart"
        const val TOLERANCE = 0.08f
    }
}
