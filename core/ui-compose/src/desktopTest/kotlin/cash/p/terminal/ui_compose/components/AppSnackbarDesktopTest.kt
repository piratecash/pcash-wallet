package cash.p.terminal.ui_compose.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.theme.darkPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppSnackbarDesktopTest {

    private val palette = darkPalette

    @Test
    fun appSnackbar_successVariant_usesSuccessFillAndTextPrimaryIconTint() = runComposeUiTest {
        val pixels = render(AppSnackbarVariant.Success, ICON_PAINTER)

        assertEquals(palette.statusSuccess, pixels.fill())
        assertTrue(palette.textPrimary in pixels.rightHalfColors())
    }

    @Test
    fun appSnackbar_errorVariant_usesErrorFillAndContentOnColorIconTint() = runComposeUiTest {
        val pixels = render(AppSnackbarVariant.Error, ICON_PAINTER)

        assertEquals(palette.statusError, pixels.fill())
        assertTrue(palette.contentOnColor in pixels.rightHalfColors())
    }

    @Test
    fun appSnackbar_messageVariant_usesNeutralFillAndContentOnColorIconTint() = runComposeUiTest {
        val pixels = render(AppSnackbarVariant.Message, ICON_PAINTER)

        assertEquals(palette.snackbarNeutralBackground, pixels.fill())
        assertTrue(palette.contentOnColor in pixels.rightHalfColors())
    }

    @Test
    fun appSnackbar_premiumVariant_usesWarningFillAndContentOnColorIconTint() = runComposeUiTest {
        val pixels = render(AppSnackbarVariant.Premium, ICON_PAINTER)

        assertEquals(palette.statusWarning, pixels.fill())
        assertTrue(palette.contentOnColor in pixels.rightHalfColors())
    }

    @Test
    fun appSnackbar_warningVariant_usesNeutralFillAndWarningIconTint() = runComposeUiTest {
        val pixels = render(AppSnackbarVariant.Warning, ICON_PAINTER)

        assertEquals(palette.snackbarNeutralBackground, pixels.fill())
        assertTrue(palette.statusWarning in pixels.rightHalfColors())
        assertFalse(palette.contentOnColor in pixels.rightHalfColors())
    }

    @Test
    fun appSnackbar_inProgressVariantWithoutIcon_drawsContentOnColorProgress() = runComposeUiTest {
        val pixels = render(AppSnackbarVariant.InProgress, icon = null)

        assertEquals(palette.snackbarNeutralBackground, pixels.fill())
        assertTrue(palette.contentOnColor in pixels.rightHalfColors())
    }

    @Test
    fun appSnackbar_nullIcon_rendersNoIcon() = runComposeUiTest {
        val pixels = render(AppSnackbarVariant.Error, icon = null)

        assertFalse(palette.contentOnColor in pixels.rightHalfColors())
    }

    private fun ComposeUiTest.render(variant: AppSnackbarVariant, icon: Painter?): SnackbarPixels {
        // The progress indicator animates forever, so idling would never finish.
        mainClock.autoAdvance = false
        setContent {
            ComposeAppTheme(darkTheme = true) {
                AppSnackbar(text = ".", variant = variant, icon = icon, modifier = Modifier.testTag(TAG))
            }
        }
        mainClock.advanceTimeBy(FRAME_TIME_MS)
        return SnackbarPixels(onNodeWithTag(TAG).captureToImage().toPixelMap())
    }

    private class SnackbarPixels(private val map: PixelMap) {
        fun fill(): Color = map[map.width / 2, map.height / 2]

        fun rightHalfColors(): Set<Color> = buildSet {
            for (x in map.width / 2 until map.width) for (y in 0 until map.height) add(map[x, y])
        }
    }

    private companion object {
        const val TAG = "snackbar"
        const val FRAME_TIME_MS = 500L
        val ICON_PAINTER = ColorPainter(Color.Magenta)
    }
}
