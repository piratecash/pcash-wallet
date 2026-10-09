package cash.p.terminal.ui_compose.theme

import android.content.Context
import android.content.res.Configuration

/** Palette for View code that has no Compose theme; follows the resources' night mode. */
fun Context.appColors(): Colors {
    val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return if (nightMask == Configuration.UI_MODE_NIGHT_YES) darkPalette else lightPalette
}
