package cash.p.terminal.modules.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.send_success_queued
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource

/**
 * Progress HUD for a send/swap, driven by [sendResult].
 *
 * The snackbar attaches to the activity content view, so it outlives the screen that showed it.
 * Driving it from a composable body therefore spawned a new snackbar on every recomposition and
 * stranded the indefinite "sending" one on screen for good once the screen left composition (for
 * example when the offline blocker replaced it) — only an app restart cleared it. Showing runs in
 * an effect keyed on [sendResult], and [DisposableEffect] ties that HUD to this composable.
 */
@Composable
fun SendResultHud(
    sendResult: SendResult?,
    sendingTextRes: StringResource,
    successTextRes: StringResource,
) {
    val hud = rememberHudMessenger()

    // Only the indefinite "sending" HUD is tracked for dismissal: the terminal ones expire on
    // their own and are meant to stay visible while the screen auto-closes.
    var sendingShown by remember { mutableStateOf(false) }

    // getDescription()/getString() are @Composable, so the failure text is resolved here rather
    // than inside the effect.
    val failureText = (sendResult as? SendResult.Failed)?.let { failed ->
        failed.caution.getDescription() ?: failed.caution.getString()
    }

    LaunchedEffect(sendResult) {
        if (sendingShown) hud.dismiss()
        sendingShown = false

        when (sendResult) {
            SendResult.Sending -> {
                hud.showInProgress(Translator.getString(sendingTextRes), SnackbarDuration.INDEFINITE)
                sendingShown = true
            }

            is SendResult.Sent -> hud.showSuccess(
                Translator.getString(successTextRes),
                SnackbarDuration.MEDIUM,
            )

            is SendResult.SentButQueued -> hud.showWarning(
                Translator.getString(Res.string.send_success_queued),
                SnackbarDuration.LONG,
            )

            is SendResult.Failed -> hud.showError(failureText.orEmpty())

            null -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (sendingShown) hud.dismiss() }
    }
}
