package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cash.p.terminal.resources.AccountRecovery_Note
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_20
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarning
import org.jetbrains.compose.resources.stringResource

@Composable
fun NoteWarning(
    modifier: Modifier = Modifier,
    text: String,
    onClick: (() -> Unit),
    onClose: (() -> Unit)?
) {
    TextImportantWarning(
        modifier = modifier.clickable(onClick = onClick),
        text = text,
        title = stringResource(Res.string.AccountRecovery_Note),
        icon = Res.drawable.ic_attention_20,
        onClose = onClose
    )
}

@Composable
fun NoteError(
    modifier: Modifier = Modifier,
    text: String,
    onClick: (() -> Unit)
) {
    TextImportantError(
        modifier = modifier.clickable(onClick = onClick),
        text = text,
        title = stringResource(Res.string.AccountRecovery_Note),
        icon = Res.drawable.ic_attention_20
    )
}
