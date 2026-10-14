package cash.p.terminal.modules.market.posts

import cash.p.terminal.ui_compose.entities.ViewState

object MarketPostsModule {

    data class UiState(
        val items: List<PostViewItem>,
        val viewState: ViewState,
        val isRefreshing: Boolean
    )

    data class PostViewItem(
        val source: String,
        val title: String,
        val body: String,
        val timeAgo: String,
        val url: String
    )

}
