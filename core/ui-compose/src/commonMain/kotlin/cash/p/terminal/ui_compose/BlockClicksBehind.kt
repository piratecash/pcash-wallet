package cash.p.terminal.ui_compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun Modifier.blockClicksBehind() = this.clickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() }
) { /* Do nothing */ }
