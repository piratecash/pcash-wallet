package cash.p.terminal.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

enum class Keyboard {
    Opened, Closed
}

/** Soft keyboard visibility; a desktop keyboard is always there, so it reports [Keyboard.Opened]. */
@Composable
expect fun observeKeyboardState(): State<Keyboard>

/** Checks whether the active input method is a third-party keyboard; desktop has none. */
@Composable
expect fun rememberThirdPartyKeyboardCheck(): () -> Boolean

/** Opens the system input method picker; a no-op on desktop. */
@Composable
expect fun rememberInputMethodPicker(): () -> Unit
