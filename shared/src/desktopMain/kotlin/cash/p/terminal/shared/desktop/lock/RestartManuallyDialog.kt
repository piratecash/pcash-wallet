package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Ok
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_forgot_password_failed
import cash.p.terminal.resources.desktop_restart_manually
import cash.p.terminal.resources.desktop_wipe_restart_manually
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import cash.p.terminal.ui_compose.components.AppDialog
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Only a successful wipe deleted the slots, so only then may the user be told the data is gone. */
internal fun restartManuallyMessages(outcome: WipeOutcome): List<StringResource> = when (outcome) {
    WipeOutcome.SUCCESS -> listOf(Res.string.desktop_wipe_restart_manually)
    WipeOutcome.FAILED -> listOf(Res.string.desktop_forgot_password_failed, Res.string.desktop_restart_manually)
}

/** The app could not start itself again after the wipe; confirming quits it. */
@Composable
internal fun RestartManuallyDialog(outcome: WipeOutcome, onConfirm: () -> Unit) {
    AppDialog(onDismissRequest = onConfirm) {
        restartManuallyMessages(outcome).forEach { message ->
            body_leah(
                text = stringResource(message),
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp),
            )
        }
        VSpacer(24.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            title = stringResource(Res.string.Button_Ok),
            onClick = onConfirm,
        )
        VSpacer(24.dp)
    }
}

@Preview
@Composable
private fun RestartManuallyDialogPreview() {
    ComposeAppTheme {
        RestartManuallyDialog(outcome = WipeOutcome.FAILED, onConfirm = {})
    }
}
