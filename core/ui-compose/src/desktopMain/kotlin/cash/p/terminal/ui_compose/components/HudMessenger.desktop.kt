package cash.p.terminal.ui_compose.components

import androidx.compose.runtime.Composable

@Composable
actual fun rememberHudMessenger(): HudMessenger = LocalHudMessenger.current
