package cash.p.terminal.shared.main

import cash.p.terminal.ui_compose.components.AppSnackbarVariant
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.SnackbarDuration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.DrawableResource

data class HudMessage(val text: String, val variant: AppSnackbarVariant, val icon: DrawableResource? = null)

/** Collects messages for the snackbar overlay of [DesktopNavHost]. */
class DesktopHudMessenger : HudMessenger {
    private val _messages = MutableSharedFlow<HudMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<HudMessage> = _messages

    override fun showSuccess(text: String, duration: SnackbarDuration) = show(text, AppSnackbarVariant.Success)

    override fun showSuccess(text: String, icon: DrawableResource) = show(text, AppSnackbarVariant.Success, icon)

    override fun showMessage(text: String, icon: DrawableResource, duration: SnackbarDuration) =
        show(text, AppSnackbarVariant.Message, icon)

    override fun showInProgress(text: String, duration: SnackbarDuration) = show(text, AppSnackbarVariant.InProgress)

    override fun showError(text: String) = show(text, AppSnackbarVariant.Error)

    override fun showWarning(text: String, duration: SnackbarDuration) = show(text, AppSnackbarVariant.Warning)

    override fun showPremium(text: String, duration: SnackbarDuration) = show(text, AppSnackbarVariant.Premium)

    override fun vibrate() = Unit

    private fun show(text: String, variant: AppSnackbarVariant, icon: DrawableResource? = null) {
        _messages.tryEmit(HudMessage(text, variant, icon))
    }
}
