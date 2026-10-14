package cash.p.terminal.ui_compose.components

import android.view.View

class HudHelperMessenger(private val view: View) : HudMessenger {
    override fun showSuccess(text: String) {
        HudHelper.showSuccessMessage(view, text)
    }

    override fun showError(text: String) {
        HudHelper.showErrorMessage(view, text)
    }

    override fun showWarning(text: String) {
        HudHelper.showWarningMessage(view, text)
    }

    override fun vibrate() = HudHelper.vibrate(view.context)
}
