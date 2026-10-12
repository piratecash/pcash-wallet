package cash.p.terminal.modules.market.overview

import cash.p.terminal.resources.SyncError
import cash.p.terminal.resources.Res
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.market.category.MarketCategoryPage
import cash.p.terminal.modules.market.platform.MarketPlatformPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.market.overview.ui.MetricChartsView
import cash.p.terminal.modules.market.overview.ui.TopPairsBoardView
import cash.p.terminal.modules.market.overview.ui.TopPlatformsBoardView
import cash.p.terminal.modules.market.overview.ui.TopSectorsBoardView
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MarketOverviewScreen(
    navigation: HSNavigation,
    viewModel: MarketOverviewViewModel = koinViewModel()
) {
    val urlOpener = LocalUrlOpener.current
    val uiState = viewModel.uiState

    val scrollState = rememberScrollState()

    HSSwipeRefresh(
        refreshing = uiState.isRefreshing,
        onRefresh = {
            viewModel.refresh()
        }
    ) {
        Crossfade(uiState.viewState, label = "") { viewState ->
            when (viewState) {
                ViewState.Loading -> {
                    Loading()
                }
                is ViewState.Error -> {
                    ListErrorView(stringResource(Res.string.SyncError), viewModel::onErrorClick)
                }
                ViewState.Success -> {
                    uiState.viewItem?.let { viewItem ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            MetricChartsView(viewItem.marketMetrics, navigation)

                            TopPairsBoardView(
                                topMarketPairs = viewItem.topMarketPairs,
                                onItemClick = {
                                    it.tradeUrl?.let {
                                        urlOpener.open(it)
                                    }
                                }
                            ) {
                            }

                            TopPlatformsBoardView(
                                viewItem.topPlatformsBoard,
                                onSelectTimeDuration = { timeDuration ->
                                    viewModel.onSelectTopPlatformsTimeDuration(timeDuration)
                                },
                                onItemClick = {
                                    navigation.slideFromRight(MarketPlatformPage(it))
                                },
                                onClickSeeAll = {
                                }
                            )

                            TopSectorsBoardView(
                                board = viewItem.topSectorsBoard
                            ) { coinCategory ->
                                navigation.slideFromBottom(MarketCategoryPage(coinCategory))
                            }

                            VSpacer(height = 32.dp)
                        }
                    }
                }
            }
        }
    }
}
