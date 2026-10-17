package cash.p.terminal.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.main.DesktopNavHost
import cash.p.terminal.ui_compose.components.UrlOpener
import org.koin.compose.koinInject

/** [navigation] is owned by the caller: a lock disposes the app without losing its back stack. */
@Composable
fun PcashApp(
    navigation: HSNavigation,
    modifier: Modifier = Modifier,
) {
    DesktopNavHost(
        navigation = navigation,
        hudMessenger = koinInject<DesktopHudMessenger>(),
        urlOpener = koinInject<UrlOpener>(),
        modifier = modifier,
    )
}
