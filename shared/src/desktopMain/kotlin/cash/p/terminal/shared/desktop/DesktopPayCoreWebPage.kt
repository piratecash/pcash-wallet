package cash.p.terminal.shared.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.paycore.webview.PayCoreWebPage
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.paycore_browser_open_again
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

/** The browser cannot report the back URL, so the user confirms instead; the button stands for that redirect. */
object DesktopPayCoreWebPage : PayCoreWebPage {
    @Composable
    override fun Content(
        url: String,
        title: String,
        completeTitle: String,
        onClose: () -> Unit,
        onInterceptBackUrl: () -> Unit,
        backUrlPrefix: String,
    ) {
        val urlOpener = LocalUrlOpener.current
        LaunchedEffect(url) { urlOpener.open(url) }

        Scaffold(
            containerColor = ComposeAppTheme.colors.backgroundBase,
            topBar = {
                AppBar(
                    title = title,
                    navigationIcon = { HsBackButton(onClick = onClose) }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth(),
                    title = completeTitle,
                    onClick = onInterceptBackUrl,
                )
                VSpacer(16.dp)
                ButtonPrimaryDefault(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.paycore_browser_open_again),
                    onClick = { urlOpener.open(url) },
                )
            }
        }
    }
}
