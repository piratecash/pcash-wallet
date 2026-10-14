package cash.p.terminal.modules.market.category

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.chart.ChartCurrencyValueFormatterShortened
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.wallet.models.CoinCategory
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.ui_compose.entities.ViewState
import io.horizontalsystems.chartview.chart.ChartViewModel
import cash.p.terminal.ui_compose.CoinFragmentInput
import io.horizontalsystems.chartview.ui.Chart
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.market.topcoins.SelectorDialogState
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.ButtonSecondaryToggle
import cash.p.terminal.ui.compose.components.CoinList
import cash.p.terminal.ui.compose.components.DescriptionCard
import cash.p.terminal.ui_compose.components.HeaderSorting
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui.compose.components.SortMenu
import cash.p.terminal.ui.compose.components.TopCloseButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SyncError
import cash.p.terminal.resources.Market_Sort_PopupTitle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class MarketCategoryPage(val input: CoinCategory) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val koin = getKoin()
        val chartViewModel = koinViewModel<ChartViewModel> {
            parametersOf(
                koin.get<CoinCategoryMarketDataChartService> { parametersOf(input.uid) },
                ChartCurrencyValueFormatterShortened(koin.get()),
            )
        }
        val viewModel = koinViewModel<MarketCategoryViewModel> { parametersOf(input) }

        CategoryScreen(
            viewModel,
            chartViewModel,
            { navigation.navigateUpSafely() },
            { coinUid -> onCoinClick(coinUid, navigation) }
        )
    }

    private fun onCoinClick(coinUid: String, navigation: HSNavigation) {
        navigation.slideFromRight(CoinPage(CoinFragmentInput(coinUid)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    viewModel: MarketCategoryViewModel,
    chartViewModel: ChartViewModel,
    onCloseButtonClick: () -> Unit,
    onCoinClick: (String) -> Unit,
) {
    var scrollToTopAfterUpdate by rememberSaveable { mutableStateOf(false) }
    val uiState = viewModel.uiState

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            TopCloseButton(onCloseButtonClick)

            HSSwipeRefresh(
                refreshing = uiState.isRefreshing,
                onRefresh = {
                    viewModel.refresh()
                }
            ) {
                Crossfade(uiState.viewState) { state ->
                    when (state) {
                        ViewState.Loading -> {
                            Loading()
                        }

                        is ViewState.Error -> {
                            ListErrorView(stringResource(Res.string.SyncError), viewModel::onErrorClick)
                        }

                        ViewState.Success -> {
                            uiState.viewItems?.let {
                                val header = uiState.header
                                val menu = uiState.menu

                                CoinList(
                                    items = it,
                                    scrollToTop = scrollToTopAfterUpdate,
                                    onAddFavorite = { uid -> viewModel.onAddFavorite(uid) },
                                    onRemoveFavorite = { uid -> viewModel.onRemoveFavorite(uid) },
                                    onCoinClick = onCoinClick,
                                    preItems = {
                                        item {
                                            DescriptionCard(header.title, header.description, header.icon)
                                        }
                                        item {
                                            Chart(
                                                uiState = chartViewModel.uiState,
                                                getSelectedPointCallback = chartViewModel::getSelectedPoint,
                                                onSelectChartInterval = chartViewModel::onSelectChartInterval)
                                        }
                                        stickyHeader {
                                            HeaderSorting(borderTop = true, borderBottom = true) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    SortMenu(
                                                        menu.sortingFieldSelect.selected.titleResId,
                                                        viewModel::showSelectorMenu
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier.padding(
                                                        start = 8.dp,
                                                        end = 16.dp
                                                    )
                                                ) {
                                                    ButtonSecondaryToggle(
                                                        select = menu.marketFieldSelect,
                                                        onSelect = viewModel::onSelectMarketField
                                                    )
                                                }
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
        //Dialog
        when (val option = uiState.selectorDialogState) {
            is SelectorDialogState.Opened -> {
                AlertGroup(
                    Res.string.Market_Sort_PopupTitle,
                    option.select,
                    { selected ->
                        scrollToTopAfterUpdate = true
                        viewModel.onSelectSortingField(selected)
                    },
                    { viewModel.onSelectorDialogDismiss() }
                )
            }

            SelectorDialogState.Closed -> {
            }
        }
    }
}
