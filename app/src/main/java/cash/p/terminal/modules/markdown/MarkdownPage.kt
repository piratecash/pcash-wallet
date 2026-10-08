package cash.p.terminal.modules.markdown

import android.os.Parcelable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class MarkdownPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        MarkdownScreen(
            showAsPopup = input.showAsPopup,
            markdownUrl = input.markdownUrl,
            onCloseClick = { navigation.navigateUpSafely() },
            onUrlClick = { url ->
                navigation.openMarkdownOrWeblink(url)
            }
        )
    }

    @Parcelize
    data class Input(
        val markdownUrl: String,
        val showAsPopup: Boolean = false,
    ) : Parcelable
}

fun HSNavigation.openMarkdownOrWeblink(url: String) {
    if (LinkHelper.isMarkdownLink(url)) {
        slideFromRight(MarkdownPage(MarkdownPage.Input(url)))
    } else {
        LinkHelper.openLinkInAppBrowser(App.instance, url)
    }
}

@Composable
private fun MarkdownScreen(
    showAsPopup: Boolean,
    markdownUrl: String,
    onCloseClick: () -> Unit,
    onUrlClick: (String) -> Unit,
    viewModel: MarkdownViewModel = viewModel(factory = MarkdownModule.Factory(markdownUrl))
) {

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            if (showAsPopup) {
                AppBar(
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.ResString(R.string.Button_Close),
                            icon = R.drawable.ic_close_24,
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
            onUrlClick = onUrlClick
        )
    }
}
