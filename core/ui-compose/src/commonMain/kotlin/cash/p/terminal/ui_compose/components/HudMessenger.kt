package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.DrawableResource

interface HudMessenger {
    fun showSuccess(text: String, duration: SnackbarDuration = SnackbarDuration.SHORT)
    fun showSuccess(text: String, icon: DrawableResource)
    fun showMessage(text: String, icon: DrawableResource, duration: SnackbarDuration)
    fun showInProgress(text: String, duration: SnackbarDuration)
    fun showError(text: String)
    fun showWarning(text: String, duration: SnackbarDuration = SnackbarDuration.SHORT)
    fun showPremium(text: String, duration: SnackbarDuration)
    fun vibrate()

    /** Hides the HUD this messenger showed last, if it is still on screen. */
    fun dismiss()
}

val LocalHudMessenger = staticCompositionLocalOf<HudMessenger> {
    error("HudMessenger is not provided")
}

/** Messenger of the current window: on Android a sheet or dialog shows the HUD inside its own window. */
@Composable
expect fun rememberHudMessenger(): HudMessenger
