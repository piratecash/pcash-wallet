package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalWindowInfo
import io.horizontalsystems.core.DesktopAppForegroundState
import org.koin.compose.koinInject

/** Mirrors window focus and minimization into [DesktopAppForegroundState]. */
@Composable
fun DesktopForegroundEffect(windowMinimized: Boolean) {
    val foregroundState = koinInject<DesktopAppForegroundState>()
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { foregroundState.setInForeground(focused) }
    LaunchedEffect(windowMinimized) { foregroundState.setWindowMinimized(windowMinimized) }
}
