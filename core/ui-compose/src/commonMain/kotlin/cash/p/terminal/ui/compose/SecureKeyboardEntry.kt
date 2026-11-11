package cash.p.terminal.ui.compose

import androidx.compose.ui.Modifier

/** Marks a secret input: on macOS other processes cannot read the keystrokes while it has focus. */
expect fun Modifier.secureKeyboardEntry(): Modifier
