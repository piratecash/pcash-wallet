package cash.p.terminal.shared.main

import cash.p.terminal.ui_compose.components.AppSnackbarVariant
import cash.p.terminal.ui_compose.components.HudMessenger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class HudMessage(val text: String, val variant: AppSnackbarVariant)

/** Collects messages for the snackbar overlay of [DesktopNavHost]. */
class DesktopHudMessenger : HudMessenger {
    private val _messages = MutableSharedFlow<HudMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<HudMessage> = _messages

    override fun showSuccess(text: String) = show(text, AppSnackbarVariant.Success)

    override fun showError(text: String) = show(text, AppSnackbarVariant.Error)

    override fun showWarning(text: String) = show(text, AppSnackbarVariant.Warning)

    override fun vibrate() = Unit

    private fun show(text: String, variant: AppSnackbarVariant) {
        _messages.tryEmit(HudMessage(text, variant))
    }
}
