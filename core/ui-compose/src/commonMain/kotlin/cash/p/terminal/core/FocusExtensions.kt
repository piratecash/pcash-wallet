package cash.p.terminal.core

import androidx.compose.ui.focus.FocusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clears focus (dismissing keyboard) and launches [action] after a brief delay.
 * Prevents a Compose bug where .imePadding() gets stuck with stale IME insets
 * when the keyboard is dismissed during fragment navigation.
 */
fun CoroutineScope.launchAfterClearingFocus(
    focusManager: FocusManager,
    action: () -> Unit
) {
    focusManager.clearFocus()
    launch {
        delay(100)
        action()
    }
}
