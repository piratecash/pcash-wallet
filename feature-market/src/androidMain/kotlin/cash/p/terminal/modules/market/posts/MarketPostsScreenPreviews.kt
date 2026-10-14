package cash.p.terminal.modules.market.posts

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui.compose.components.CellNews

@Preview
@Composable
private fun MarketPostViewPreview() {
    val postItem = MarketPostsModule.PostViewItem(
        "Tidal",
        "3iQ's The Ether Fund begins \$CAD trading on TSX after Bitcoin The Ether Fund begins after Bitcoin",
        "Traders in East Asia are ready to take on more built by Wipro to streamline its liquefied.",
        "1h ago",
        "https://www.binance.org/news"
    )
    ComposeAppTheme {
        CellNews(
            source = postItem.source,
            title = postItem.title,
            body = postItem.body,
            date = postItem.timeAgo
        ) {}
    }
}
