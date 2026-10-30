package cash.p.terminal.modules.memo

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.address.MemoPrefill
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_DialogMemoHint
import cash.p.terminal.resources.send_memo_too_long

@Composable
fun HSMemoInput(
    maxLength: Int,
    memoPrefill: MemoPrefill,
    onValueChange: (String) -> Unit,
    initial: String = "",
    visible: Boolean = true,
    maxBytes: Int? = null,
) {
    var memo by rememberSaveable { mutableStateOf(initial) }
    var isInitialized by rememberSaveable { mutableStateOf(initial.isNotEmpty()) }
    var handledEventInComposition by remember { mutableStateOf(false) }
    val currentMemoPrefill by rememberUpdatedState(memoPrefill)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(memoPrefill.event?.id, visible) {
        val event = currentMemoPrefill.event
        if (event != null) {
            if (!event.isNavigationPrefill || !isInitialized) {
                memo = event.memo.takeIf { maxBytes != null || it.length <= maxLength }.orEmpty()
                isInitialized = true
            }
            if (visible) currentOnValueChange(memo)
            handledEventInComposition = true
            currentMemoPrefill.handled(event.id)
        } else if (visible && isInitialized && !handledEventInComposition) {
            currentOnValueChange(memo)
        }
        if (!visible) handledEventInComposition = false
    }

    if (!visible) return
    val fits = if (maxBytes == null) memo.length <= maxLength else memo.fitsMemoBytes(maxBytes)
    val tooLongError = if (!fits && maxBytes != null) {
        val message = stringResource(Res.string.send_memo_too_long, memo.encodeToByteArray().size, maxBytes)
        DataState.Error(Exception(message))
    } else {
        null
    }
    FormsInput(
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = fits,
            initial = memo,
            hint = stringResource(Res.string.Send_DialogMemoHint),
            hintColor = ComposeAppTheme.colors.textDisabled,
            hintStyle = ComposeAppTheme.typography.bodyItalic,
            textColor = ComposeAppTheme.colors.textPrimary,
            textStyle = ComposeAppTheme.typography.bodyItalic,
            pasteEnabled = false,
            singleLine = true,
            maxLength = maxLength,
            maxBytes = maxBytes,
            state = tooLongError,
            onValueChange = {
                memo = it
                isInitialized = true
                onValueChange(it)
            }
    )
}

/** Blank counts as no memo: every consumer drops it before sending. */
fun String?.fitsMemoBytes(maxBytes: Int): Boolean = isNullOrBlank() || encodeToByteArray().size <= maxBytes
