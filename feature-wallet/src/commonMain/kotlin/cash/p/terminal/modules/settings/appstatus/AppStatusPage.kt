package cash.p.terminal.modules.settings.appstatus

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.settings.appcache.AppCachePage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel

class AppStatusPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AppStatusScreen(
            viewModel = koinViewModel(),
            onBack = navigation::navigateUpSafely,
            onCacheClick = { navigation.slideFromRight(AppCachePage()) },
        )
    }
}
