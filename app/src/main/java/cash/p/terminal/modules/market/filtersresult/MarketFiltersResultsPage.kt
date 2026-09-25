package cash.p.terminal.modules.market.filtersresult

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.market.filters.MarketFiltersModule
import cash.p.terminal.modules.market.filters.MarketFiltersPage
import cash.p.terminal.modules.market.filters.MarketFiltersViewModel
import cash.p.terminal.modules.market.topcoins.OptionController
import cash.p.terminal.ui.compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui.compose.components.CoinList
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderSorting
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class MarketFiltersResultsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val marketFiltersViewModel = viewModel<MarketFiltersViewModel>(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(MarketFiltersPage::class),
            factory = MarketFiltersModule.Factory()
        )
        val viewModel = viewModel<MarketFiltersResultViewModel>(
            factory = MarketFiltersResultsModule.Factory(marketFiltersViewModel.service)
        )

        SearchResultsScreen(viewModel, navigation)
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsScreen(
    viewModel: MarketFiltersResultViewModel,
    navigation: HSNavigation
) {

    val uiState = viewModel.uiState
    var scrollToTopAfterUpdate by rememberSaveable { mutableStateOf(false) }
    var openSortingSelector by rememberSaveable { mutableStateOf(false) }

    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(
                title = stringResource(R.string.Market_AdvancedSearch_Results),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                },
            )

            Crossfade(uiState.viewState, label = "") { state ->
                when (state) {
                    ViewState.Loading -> {
                        Loading()
                    }

                    is ViewState.Error -> {
                        ListErrorView(stringResource(R.string.SyncError), viewModel::onErrorClick)
                    }

                    ViewState.Success -> {
                        CoinList(
                            items = uiState.viewItems,
                            scrollToTop = scrollToTopAfterUpdate,
                            onAddFavorite = { uid ->
                                viewModel.onAddFavorite(uid)
                            },
                            onRemoveFavorite = { uid ->
                                viewModel.onRemoveFavorite(uid)
                            },
                            onCoinClick = { coinUid ->
                                navigation.slideFromRight(CoinPage(CoinFragmentInput(coinUid)))
                            },
                            preItems = {
                                stickyHeader {
                                    HeaderSorting(borderBottom = true, borderTop = true) {
                                        HSpacer(width = 16.dp)
                                        OptionController(
                                            uiState.sortingField.titleResId,
                                            onOptionClick = {
                                                openSortingSelector = true
                                            }
                                        )
                                        HSpacer(width = 16.dp)
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

            if (openSortingSelector) {
                AlertGroup(
                    title = R.string.Market_Sort_PopupTitle,
                    select = uiState.selectSortingField,
                    onSelect = { selected ->
                        viewModel.onSelectSortingField(selected)
                        openSortingSelector = false
                        scrollToTopAfterUpdate = true
                    },
                    onDismiss = {
                        openSortingSelector = false
                    }
                )
            }

        }
    }
}
