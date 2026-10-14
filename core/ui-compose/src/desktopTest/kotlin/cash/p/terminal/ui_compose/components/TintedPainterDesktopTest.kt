package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TintedPainterDesktopTest {

    @Test
    fun tinted_drawsBaseWithTintColor() = runComposeUiTest {
        setContent {
            Box(Modifier.size(20.dp).testTag(TAG)) {
                Image(ColorPainter(Color.Red).tinted(Color.Blue), null, Modifier.size(20.dp))
            }
        }

        assertEquals(Color.Blue, centerPixel(onNodeWithTag(TAG).captureToImage()))
    }

    @Test
    fun placeholderPainter_coinPlaceholder_isMoreTransparentThanRawResource() = runComposeUiTest {
        setContent {
            ComposeAppTheme(darkTheme = true) {
                Column {
                    Box(Modifier.size(40.dp).background(Color.Black).testTag(RAW_TAG)) {
                        Image(painterResource(Res.drawable.coin_placeholder), null, Modifier.size(40.dp))
                    }
                    Box(Modifier.size(40.dp).background(Color.Black).testTag(TINTED_TAG)) {
                        Image(placeholderPainter(Res.drawable.coin_placeholder), null, Modifier.size(40.dp))
                    }
                }
            }
        }
        waitForIdle()

        val raw = centerPixel(onNodeWithTag(RAW_TAG).captureToImage())
        val tinted = centerPixel(onNodeWithTag(TINTED_TAG).captureToImage())

        assertTrue(tinted.red < raw.red, "tinted=$tinted raw=$raw")
    }

    private fun centerPixel(image: ImageBitmap): Color =
        image.toPixelMap().let { it[it.width / 2, it.height / 2] }

    private companion object {
        const val TAG = "probe"
        const val RAW_TAG = "raw"
        const val TINTED_TAG = "tinted"
    }
}
