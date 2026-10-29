package cash.p.terminal.modules.settings.appcache

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

class AppCachePage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AppCacheScreen(navigation)
    }
}
