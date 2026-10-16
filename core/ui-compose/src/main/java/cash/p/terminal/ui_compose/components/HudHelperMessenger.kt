package cash.p.terminal.ui_compose.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import org.jetbrains.compose.resources.DrawableResource

class HudHelperMessenger(private val view: View) : HudMessenger {
    override fun showSuccess(text: String, duration: SnackbarDuration) {
        HudHelper.showSuccessMessage(view, text, duration)
    }

    override fun showSuccess(text: String, icon: DrawableResource) {
        HudHelper.showSuccessMessage(view, text, icon)
    }

    override fun showError(text: String) {
        HudHelper.showErrorMessage(view, text)
    }

    override fun showWarning(text: String) {
        HudHelper.showWarningMessage(view, text)
    }

    override fun vibrate() = HudHelper.vibrate(view.context)
}

@Composable
actual fun rememberHudMessenger(): HudMessenger {
    val view = LocalView.current
    return remember(view) { HudHelperMessenger(view) }
}
