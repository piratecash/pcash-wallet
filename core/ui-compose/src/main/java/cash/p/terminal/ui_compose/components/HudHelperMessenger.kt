package cash.p.terminal.ui_compose.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import org.jetbrains.compose.resources.DrawableResource

class HudHelperMessenger(private val view: View) : HudMessenger {
    private var shown: CustomSnackbar? = null

    override fun showSuccess(text: String, duration: SnackbarDuration) {
        shown = HudHelper.showSuccessMessage(view, text, duration)
    }

    override fun showSuccess(text: String, icon: DrawableResource) {
        shown = HudHelper.showSuccessMessage(view, text, icon)
    }

    override fun showMessage(text: String, icon: DrawableResource, duration: SnackbarDuration) {
        shown = HudHelper.showMessage(view, text, duration, iconResource = icon)
    }

    override fun showInProgress(text: String, duration: SnackbarDuration) {
        shown = HudHelper.showInProcessMessage(view, text, duration)
    }

    override fun showError(text: String) {
        shown = HudHelper.showErrorMessage(view, text)
    }

    override fun showWarning(text: String, duration: SnackbarDuration) {
        shown = HudHelper.showWarningMessage(view, text, duration)
    }

    override fun showPremium(text: String, duration: SnackbarDuration) {
        shown = HudHelper.showPremiumMessage(view, text, duration)
    }

    override fun vibrate() = HudHelper.vibrate(view.context)

    override fun dismiss() {
        shown?.dismiss()
        shown = null
    }
}

@Composable
actual fun rememberHudMessenger(): HudMessenger {
    val view = LocalView.current
    return remember(view) { HudHelperMessenger(view) }
}
