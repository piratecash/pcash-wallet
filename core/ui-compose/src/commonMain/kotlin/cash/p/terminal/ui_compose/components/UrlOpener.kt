package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.staticCompositionLocalOf

interface UrlOpener {
    fun open(url: String)
}

val LocalUrlOpener = staticCompositionLocalOf<UrlOpener> {
    error("UrlOpener is not provided")
}
