package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

internal actual fun appDialogProperties() =
    DialogProperties(usePlatformDefaultWidth = false, scrimColor = Color.Transparent)

@Composable
internal actual fun DisablePlatformDialogDim() = Unit
