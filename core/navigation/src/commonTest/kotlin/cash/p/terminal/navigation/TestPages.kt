package cash.p.terminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

internal open class TestPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) = Unit
}

internal open class TestSheet : HSBottomSheet() {
    @Composable
    override fun GetContent(navigation: HSNavigation) = Unit
}

internal class RootPage : TestPage()
internal class SendPage : TestPage()
internal class PinPage : TestPage()
internal class ConfirmPage : TestPage()
internal class RiskyAddressSheet : TestSheet()
internal class OptionsSheet : TestSheet()

/** Calls navigateUpSafely once [closeRequested] is set, like a state-driven close. */
internal class SelfClosingPage(bottomSheet: Boolean = false) : HSPage(bottomSheet = bottomSheet) {
    var closeRequested by mutableStateOf(false)
    var composed = false
        private set
    var entryLifecycle: Lifecycle? = null
        private set

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        SideEffect { entryLifecycle = lifecycle }
        DisposableEffect(Unit) {
            composed = true
            onDispose { composed = false }
        }
        LaunchedEffect(closeRequested) {
            if (closeRequested) navigation.navigateUpSafely()
        }
    }
}
