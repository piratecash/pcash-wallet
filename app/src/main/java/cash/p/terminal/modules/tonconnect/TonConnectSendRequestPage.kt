package cash.p.terminal.modules.tonconnect

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

class TonConnectSendRequestPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        TonConnectSendRequestScreen(navigation)
    }
}
