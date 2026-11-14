package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import kotlinx.coroutines.flow.first
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import java.awt.Taskbar
import java.awt.Window

/** A sign request for a visible wallet brings a background window forward, as the phone shows its sheet at once. */
@Composable
fun FrameWindowScope.DesktopTonConnectAttentionEffect(windowState: WindowState) {
    val vault = koinInject<DesktopVault>()
    val koin = getKoin()
    LaunchedEffect(vault) {
        // Built over the storage, which opens at the first unlock; the manager starts no kit before that.
        vault.state.first { it is DesktopVaultState.Unlocked }
        val tonConnectManager = koin.get<TonConnectManager>()
        tonConnectManager.sendRequestArrivals.collect {
            if (window.isFocused) return@collect
            windowState.isMinimized = false
            window.toFront()
            requestUserAttention(window)
        }
    }
}

private fun requestUserAttention(window: Window) {
    if (!Taskbar.isTaskbarSupported()) return
    val taskbar = Taskbar.getTaskbar()
    when {
        taskbar.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW) -> taskbar.requestWindowUserAttention(window)
        taskbar.isSupported(Taskbar.Feature.USER_ATTENTION) -> taskbar.requestUserAttention(true, false)
    }
}
