package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import cash.p.terminal.core.tryOrNull
import java.awt.Toolkit
import java.awt.event.KeyEvent

/**
 * AWT has no Caps Lock listener, so the state is polled: when the window takes focus and on every
 * key event of a password field. Toolkits that do not support the query report it as off.
 */
internal class DesktopCapsLockState {

    var isOn by mutableStateOf(read())
        private set

    fun refresh() {
        isOn = read()
    }

    private fun read(): Boolean =
        tryOrNull { Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK) } ?: false
}

@Composable
internal fun rememberDesktopCapsLockState(): DesktopCapsLockState {
    val state = remember { DesktopCapsLockState() }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (focused) state.refresh()
    }
    return state
}
