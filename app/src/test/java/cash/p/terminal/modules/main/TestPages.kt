package cash.p.terminal.modules.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

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

internal class TestSheet : HSBottomSheet() {
    @Composable
    override fun GetContent(navigation: HSNavigation) = Unit
}
