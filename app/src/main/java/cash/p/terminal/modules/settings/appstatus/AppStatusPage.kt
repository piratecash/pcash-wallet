package cash.p.terminal.modules.settings.appstatus

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

class AppStatusPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AppStatusScreen(navigation)
    }
}
