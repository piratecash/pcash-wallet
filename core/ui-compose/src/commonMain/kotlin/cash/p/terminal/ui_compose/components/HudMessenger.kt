package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.staticCompositionLocalOf

interface HudMessenger {
    fun showSuccess(text: String)
    fun showError(text: String)
    fun showWarning(text: String)
    fun vibrate()
}

val LocalHudMessenger = staticCompositionLocalOf<HudMessenger> {
    error("HudMessenger is not provided")
}
