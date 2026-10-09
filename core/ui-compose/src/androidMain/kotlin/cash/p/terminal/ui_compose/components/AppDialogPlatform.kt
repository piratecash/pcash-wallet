package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

internal actual fun appDialogProperties() =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

@Composable
internal actual fun DisablePlatformDialogDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(0f) }
}
