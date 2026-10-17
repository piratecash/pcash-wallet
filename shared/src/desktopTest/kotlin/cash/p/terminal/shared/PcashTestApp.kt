package cash.p.terminal.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.main.DesktopMainPage

/** The app with a back stack of its own, which in production the desktop root hands in. */
@Composable
internal fun PcashTestApp() {
    PcashApp(remember { HSNavigation(NavBackStack<HSPage>(DesktopMainPage("desktop"))) })
}
