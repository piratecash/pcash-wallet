package cash.p.terminal.modules.market.platform

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.chart.ChartCurrencyValueFormatterShortened
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.market.topcoins.OptionController
import cash.p.terminal.modules.market.topplatforms.Platform
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SyncError
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui.compose.components.CoinList
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderSorting
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.chartview.ui.Chart
import cash.p.terminal.resources.Market_Sort_PopupTitle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MarketPlatformPage(val input: Platform) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val koin = getKoin()
        val viewModel = koinViewModel<MarketPlatformViewModel> { parametersOf(input) }
        val chartViewModel = koinViewModel<ChartViewModel> {
            parametersOf(
                koin.get<PlatformChartService> { parametersOf(input) },
                ChartCurrencyValueFormatterShortened(koin.get()),
            )
        }

        PlatformScreen(
            viewModel = viewModel,
            chartViewModel = chartViewModel,
            onCloseButtonClick = { navigation.navigateUpSafely() },
            onCoinClick = { coinUid ->
                navigation.slideFromRight(CoinPage(CoinFragmentInput(coinUid)))
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlatformScreen(
    viewModel: MarketPlatformViewModel,
    chartViewModel: ChartViewModel,
    onCloseButtonClick: () -> Unit,
    onCoinClick: (String) -> Unit,
) {

    val uiState = viewModel.uiState
    var scrollToTopAfterUpdate by rememberSaveable { mutableStateOf(false) }
    var openSortingSelector by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppBar(
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onCloseButtonClick
                    )
                )
            )
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            HSSwipeRefresh(
                refreshing = uiState.isRefreshing,
                onRefresh = {
                    viewModel.refresh()
                }
            ) {
                Crossfade(uiState.viewState, label = "") { state ->
                    when (state) {
                        ViewState.Loading -> {
                            Loading()
                        }

                        is ViewState.Error -> {
                            ListErrorView(
                                stringResource(Res.string.SyncError),
                                viewModel::onErrorClick
                            )
                        }

                        ViewState.Success -> {
                            uiState.viewItems.let { viewItems ->
                                CoinList(
                                    items = viewItems,
                                    scrollToTop = scrollToTopAfterUpdate,
                                    onAddFavorite = { uid ->
                                        viewModel.onAddFavorite(uid)
                                    },
                                    onRemoveFavorite = { uid ->
                                        viewModel.onRemoveFavorite(uid)
                                    },
                                    onCoinClick = onCoinClick,
                                    preItems = {
                                        viewModel.header.let {
                                            item {
                                                HeaderContent(it.title, it.description, it.icon)
                                            }
                                        }
                                        item {
                                            Chart(
                                                uiState = chartViewModel.uiState,
                                                getSelectedPointCallback = chartViewModel::getSelectedPoint,
                                                onSelectChartInterval = chartViewModel::onSelectChartInterval
                                            )
                                        }
                                        stickyHeader {
                                            HeaderSorting(borderTop = true, borderBottom = true) {
                                                HSpacer(width = 16.dp)
                                                OptionController(
                                                    uiState.sortingField.titleResId,
                                                    onOptionClick = {
                                                        openSortingSelector = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                                if (scrollToTopAfterUpdate) {
                                    scrollToTopAfterUpdate = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (openSortingSelector) {
        AlertGroup(
            Res.string.Market_Sort_PopupTitle,
            Select(uiState.sortingField, viewModel.sortingFields),
            { selected ->
                scrollToTopAfterUpdate = true
                viewModel.onSelectSortingField(selected)
                openSortingSelector = false
            },
            { openSortingSelector = false }
        )
    }
}

@Composable
internal fun HeaderContent(title: String, description: String, image: ImageSource) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(ComposeAppTheme.colors.backgroundBase)
    ) {
        Column(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 16.dp)
                .weight(1f)
        ) {
            title3_leah(
                text = title,
            )
            subhead2_grey(
                text = description,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Image(
            painter = image.painter(),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 24.dp)
                .size(32.dp),
        )
    }
}
