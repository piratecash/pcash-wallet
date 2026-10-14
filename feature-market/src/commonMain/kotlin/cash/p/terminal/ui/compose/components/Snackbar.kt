package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import cash.p.terminal.ui_compose.components.LocalHudMessenger

@Composable
fun SnackbarError(errorMessage: String) {
    LocalHudMessenger.current.showError(errorMessage)
}
