package cash.p.terminal.modules.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.LocalConnectionPanelState

internal open class PlainTestPage(screenshotEnabled: Boolean = true) :
    HSPage(screenshotEnabled = screenshotEnabled, showConnectionPanel = false) {
    @Composable
    override fun GetContent(navigation: HSNavigation) = Unit
}

internal class SecureTestPage : PlainTestPage(screenshotEnabled = false) {
    var composed = false
        private set

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        DisposableEffect(Unit) {
            composed = true
            onDispose { composed = false }
        }
    }
}

/** Hosts a production screen and reports whether its composition is alive. */
internal class ScreenTestPage(private val screen: @Composable (HSNavigation) -> Unit) :
    HSPage(showConnectionPanel = false) {
    var composed = false
        private set

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        DisposableEffect(Unit) {
            composed = true
            onDispose { composed = false }
        }
        screen(navigation)
    }
}

internal class TestSheet : HSBottomSheet() {
    @Composable
    override fun GetContent(navigation: HSNavigation) = Unit
}

/** [Nav3Host] as MainActivity hosts it, unlocked. */
@Composable
internal fun TestNav3Host(navigation: HSNavigation) {
    val connectionPanelState = remember { mutableStateOf(false) }
    val isLocked = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalConnectionPanelState provides connectionPanelState) {
        Nav3Host(navigation, isLocked = isLocked)
    }
}
