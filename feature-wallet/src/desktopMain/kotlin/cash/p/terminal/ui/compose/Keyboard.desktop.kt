package cash.p.terminal.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun observeKeyboardState(): State<Keyboard> = remember { mutableStateOf(Keyboard.Opened) }

@Composable
actual fun rememberThirdPartyKeyboardCheck(): () -> Boolean = { false }

@Composable
actual fun rememberInputMethodPicker(): () -> Unit = {}
