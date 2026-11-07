package cash.p.terminal.modules.markdown

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.net.MalformedURLException
import java.net.URL

class MarkdownPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val platformPages = koinInject<WalletPlatformPages>()
        MarkdownScreen(
            showAsPopup = input.showAsPopup,
            markdownUrl = input.markdownUrl,
            onCloseClick = { navigation.navigateUpSafely() },
            onUrlClick = { url -> platformPages.openMarkdownOrWeblink(navigation, url) }
        )
    }

    data class Input(
        val markdownUrl: String,
        val showAsPopup: Boolean = false,
    )
}

fun isMarkdownLink(link: String): Boolean {
    val url = validUrlOrNull(link) ?: return false
    return url.endsWith(".md", ignoreCase = true) || url.endsWith(".markdown", ignoreCase = true)
}

/** Returns [urlString] as a valid URL, adding the https scheme when it has none. */
fun validUrlOrNull(urlString: String): String? {
    if (urlString.isBlank()) return null
    return (urlOrNull(urlString) ?: urlOrNull("https://$urlString"))?.toString()
}

private fun urlOrNull(urlString: String): URL? = try {
    URL(urlString)
} catch (e: MalformedURLException) {
    null
}

@Composable
private fun MarkdownScreen(
    showAsPopup: Boolean,
    markdownUrl: String,
    onCloseClick: () -> Unit,
    onUrlClick: (String) -> Unit,
    viewModel: MarkdownViewModel = koinViewModel { parametersOf(markdownUrl) }
) {

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            if (showAsPopup) {
                AppBar(
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.ResString(Res.string.Button_Close),
                            icon = Res.drawable.ic_close_24,
                            onClick = onCloseClick
                        )
                    )
                )
            } else {
                AppBar(navigationIcon = { HsBackButton(onClick = onCloseClick) })
            }
        }
    ) {
        MarkdownContent(
            modifier = Modifier.padding(it),
            viewState = viewModel.viewState,
            markdownContent = viewModel.markdownContent,
            addFooter = true,
            onRetryClick = { viewModel.retry() },
            onUrlClick = onUrlClick,
            baseUrl = markdownUrl,
        )
    }
}
