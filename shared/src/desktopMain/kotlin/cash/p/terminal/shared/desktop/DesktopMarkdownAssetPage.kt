package cash.p.terminal.shared.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.feature.wallet.LocalizedAssetReader
import cash.p.terminal.modules.markdown.MarkdownContent
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.koinInject

/** A bundled markdown file in the current language, shown as a popup as Android shows its FAQ assets. */
class DesktopMarkdownAssetPage(private val assetPrefix: String) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val reader = koinInject<LocalizedAssetReader>()
        val urlOpener = koinInject<UrlOpener>()
        val markdown by produceState<String?>(initialValue = null, assetPrefix) { value = reader.read(assetPrefix) }
        MarkdownAssetContent(
            markdown = markdown,
            onCloseClick = navigation::navigateUpSafely,
            onUrlClick = urlOpener::open,
        )
    }
}

@Composable
private fun MarkdownAssetContent(markdown: String?, onCloseClick: () -> Unit, onUrlClick: (String) -> Unit) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onCloseClick,
                    )
                )
            )
        }
    ) { paddingValues ->
        MarkdownContent(
            modifier = Modifier.padding(paddingValues),
            viewState = if (markdown == null) ViewState.Loading else ViewState.Success,
            markdownContent = markdown,
            addFooter = true,
            onRetryClick = {},
            onUrlClick = onUrlClick,
        )
    }
}

@Preview
@Composable
private fun MarkdownAssetContentPreview() {
    ComposeAppTheme {
        MarkdownAssetContent(markdown = "# Private keys\n\nNever share them.", onCloseClick = {}, onUrlClick = {})
    }
}
