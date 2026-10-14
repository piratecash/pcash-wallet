package cash.p.terminal.modules.market.posts

import cash.p.terminal.resources.SyncError
import cash.p.terminal.resources.Res
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.ui.compose.components.CellNews
import cash.p.terminal.ui.compose.components.ListErrorView

@Composable
fun MarketPostsScreen(viewModel: MarketPostsViewModel = koinViewModel()) {
    val uiState = viewModel.uiState
    val urlOpener = LocalUrlOpener.current

    HSSwipeRefresh(
        refreshing = uiState.isRefreshing,
        onRefresh = {
            viewModel.refresh()
        }
    ) {
        Crossfade(uiState.viewState) { viewState ->
            when (viewState) {
                ViewState.Loading -> {
                    Loading()
                }
                is ViewState.Error -> {
                    ListErrorView(stringResource(Res.string.SyncError), viewModel::onErrorClick)
                }
                ViewState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items) { postItem ->
                            Spacer(modifier = Modifier.height(12.dp))
                            CellNews(
                                source = postItem.source,
                                title = postItem.title,
                                body = postItem.body,
                                date = postItem.timeAgo,
                            ) {
                                urlOpener.open(postItem.url)
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}
