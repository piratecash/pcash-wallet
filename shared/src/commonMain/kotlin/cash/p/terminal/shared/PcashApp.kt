package cash.p.terminal.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.shared.main.DesktopNavHost
import cash.p.terminal.ui_compose.components.UrlOpener
import org.koin.compose.koinInject

@Composable
fun PcashApp(
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val navigation = remember { HSNavigation(NavBackStack<HSPage>(DesktopMainPage(appVersion))) }
    DesktopNavHost(
        navigation = navigation,
        hudMessenger = koinInject<DesktopHudMessenger>(),
        urlOpener = koinInject<UrlOpener>(),
        modifier = modifier,
    )
}
