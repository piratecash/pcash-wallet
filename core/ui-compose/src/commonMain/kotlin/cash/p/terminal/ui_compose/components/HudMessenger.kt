package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.DrawableResource

interface HudMessenger {
    fun showSuccess(text: String, duration: SnackbarDuration = SnackbarDuration.SHORT)
    fun showSuccess(text: String, icon: DrawableResource)
    fun showError(text: String)
    fun showWarning(text: String)
    fun vibrate()
}

val LocalHudMessenger = staticCompositionLocalOf<HudMessenger> {
    error("HudMessenger is not provided")
}

/** Messenger of the current window: on Android a sheet or dialog shows the HUD inside its own window. */
@Composable
expect fun rememberHudMessenger(): HudMessenger
