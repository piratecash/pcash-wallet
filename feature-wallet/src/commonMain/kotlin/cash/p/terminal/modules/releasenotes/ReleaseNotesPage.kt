package cash.p.terminal.modules.releasenotes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cash.p.terminal.modules.markdown.MarkdownContent
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.CoinPage_Reddit
import cash.p.terminal.resources.CoinPage_Telegram
import cash.p.terminal.resources.CoinPage_Twitter
import cash.p.terminal.resources.ReleaseNotes_JoinUnstoppables
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_reddit_filled_24
import cash.p.terminal.resources.ic_telegram_filled_24
import cash.p.terminal.resources.ic_twitter_filled_24
import cash.p.terminal.resources.show_changelog_after_update
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ConnectionStatusView
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_brand
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class ReleaseNotesPage(val input: Input) : HSPage(showConnectionPanel = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: ReleaseNotesViewModel = koinViewModel()

        ReleaseNotesScreen(
            closeablePopup = input.showAsClosablePopup,
            uiState = viewModel.uiState,
            onCloseClick = navigation::navigateUpSafely,
            onRetryClick = viewModel::retry,
            onWhatsNewShown = viewModel::whatsNewShown,
            onShowChangelogToggle = viewModel::setShowChangeLogAfterUpdate
        )
    }

    data class Input(val showAsClosablePopup: Boolean)
}

@Composable
fun ReleaseNotesScreen(
    closeablePopup: Boolean,
    uiState: ReleaseNotesUiState,
    onCloseClick: () -> Unit,
    onRetryClick: () -> Unit,
    onWhatsNewShown: () -> Unit,
    onShowChangelogToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onUrlClick: (String) -> Unit = {},
) {
    val onDismiss = {
        onWhatsNewShown()
        onCloseClick()
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = onDismiss,
    )

    Scaffold(
        modifier = modifier,
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = { ReleaseNotesAppBar(closeablePopup, onCloseClick, onDismiss) }
    ) { paddingValues ->
        Column(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            MarkdownContent(
                modifier = Modifier.weight(1f),
                viewState = uiState.viewState,
                markdownContent = uiState.markdownContent,
                addFooter = true,
                onRetryClick = onRetryClick,
                onUrlClick = onUrlClick
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = ComposeAppTheme.colors.borderDivider,
            )

            ConnectionStatusView()
            ShowChangelogRow(uiState.showChangelogAfterUpdate, onShowChangelogToggle)
            SocialLinksRow(uiState, paddingValues)
        }
    }
}

@Composable
private fun ReleaseNotesAppBar(closeablePopup: Boolean, onCloseClick: () -> Unit, onDismiss: () -> Unit) {
    if (closeablePopup) {
        AppBar(
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(Res.string.Button_Close),
                    icon = Res.drawable.ic_close_24,
                    onClick = onDismiss
                )
            )
        )
    } else {
        AppBar(navigationIcon = { HsBackButton(onClick = onCloseClick) })
    }
}

@Composable
private fun ShowChangelogRow(checked: Boolean, onToggle: () -> Unit) {
    RowUniversal(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.backgroundBase)
            .padding(horizontal = 16.dp),
        onClick = onToggle,
    ) {
        body_leah(
            text = stringResource(Res.string.show_changelog_after_update),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp)
        )
        HsSwitch(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
private fun SocialLinksRow(uiState: ReleaseNotesUiState, paddingValues: PaddingValues) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.backgroundBase)
            .padding(bottom = paddingValues.calculateBottomPadding())
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.padding(start = 16.dp))

        SocialLinkButton(
            Res.drawable.ic_twitter_filled_24, uiState.twitterUrl, stringResource(Res.string.CoinPage_Twitter)
        )
        SocialLinkButton(
            Res.drawable.ic_telegram_filled_24, uiState.telegramUrl, stringResource(Res.string.CoinPage_Telegram)
        )
        SocialLinkButton(
            Res.drawable.ic_reddit_filled_24, uiState.redditUrl, stringResource(Res.string.CoinPage_Reddit)
        )

        Spacer(Modifier.weight(1f))

        caption_brand(
            modifier = Modifier.padding(end = 24.dp),
            text = stringResource(Res.string.ReleaseNotes_JoinUnstoppables)
        )
    }
}

@Composable
private fun SocialLinkButton(icon: DrawableResource, url: String, description: String) {
    val urlOpener = LocalUrlOpener.current
    HsIconButton(onClick = { urlOpener.open(url) }) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = ComposeAppTheme.colors.brandDefault
        )
    }
}
