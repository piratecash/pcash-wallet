package cash.p.terminal.modules.market.favorites

import cash.p.terminal.resources.Market_Signals
import cash.p.terminal.resources.Market_Tab_Watchlist_EmptyList
import cash.p.terminal.resources.SyncError
import cash.p.terminal.resources.ic_rate_24
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.viewModelStoreOwnerForRoot
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.market.topcoins.OptionController
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.ButtonSecondaryYellow
import cash.p.terminal.ui_compose.components.SecondaryButtonDefaults
import cash.p.terminal.ui.compose.components.CoinListOrderable
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderSorting
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_edit_20
import cash.p.terminal.resources.CoinPage_Period
import cash.p.terminal.resources.Market_Sort_PopupTitle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarketFavoritesScreen(
    navigation: HSNavigation
) {
    val viewModel = koinViewModel<MarketFavoritesViewModel>(
        viewModelStoreOwner = navigation.viewModelStoreOwnerForRoot()
    )
    val uiState = viewModel.uiState
    var openSortingSelector by rememberSaveable { mutableStateOf(false) }
    var openPeriodSelector by rememberSaveable { mutableStateOf(false) }
    var scrollToTopAfterUpdate by rememberSaveable { mutableStateOf(false) }
    var manualOrderEnabled by rememberSaveable { mutableStateOf(false) }

    HSSwipeRefresh(
        refreshing = uiState.isRefreshing,
        onRefresh = {
            viewModel.refresh()
        }
    ) {
        Crossfade(
            targetState = uiState.viewState,
            label = ""
        ) { viewState ->
            when (viewState) {
                ViewState.Loading -> {
                    Loading()
                }

                is ViewState.Error -> {
                    ListErrorView(stringResource(Res.string.SyncError), viewModel::onErrorClick)
                }

                ViewState.Success -> {
                    if (uiState.viewItems.isEmpty()) {
                        ListEmptyView(
                            text = stringResource(Res.string.Market_Tab_Watchlist_EmptyList),
                            icon = Res.drawable.ic_rate_24
                        )
                    } else {
                        if (uiState.showSignalsInfo) {
                            viewModel.onSignalsInfoShown()

                            navigation.slideFromBottom(MarketSignalsPage())
                        }

                        CoinListOrderable(
                            items = uiState.viewItems,
                            scrollToTop = scrollToTopAfterUpdate,
                            onAddFavorite = { /*not used */ },
                            onRemoveFavorite = { uid ->
                                viewModel.removeFromFavorites(uid)
                            },
                            onCoinClick = { coinUid ->
                                navigation.slideFromRight(CoinPage(CoinFragmentInput(coinUid)))
                            },
                            onReorder = { from, to ->
                                viewModel.reorder(from, to)
                            },
                            canReorder = uiState.sortingField == WatchlistSorting.Manual,
                            showReorderArrows = uiState.sortingField == WatchlistSorting.Manual && manualOrderEnabled,
                            enableManualOrder = {
                                manualOrderEnabled = true
                            },
                            preItems = {
                                stickyHeader {
                                    HeaderSorting(
                                        borderBottom = true,
                                    ) {
                                        HSpacer(width = 16.dp)
                                        OptionController(
                                            uiState.sortingField.titleResId,
                                            onOptionClick = {
                                                openSortingSelector = true
                                            }
                                        )
                                        if (uiState.sortingField == WatchlistSorting.Manual) {
                                            HSpacer(width = 12.dp)
                                            ButtonSecondaryCircle(
                                                icon = Res.drawable.ic_edit_20,
                                                onClick = { manualOrderEnabled = !manualOrderEnabled },
                                                tint = if (manualOrderEnabled) {
                                                    ComposeAppTheme.colors.buttonPrimaryBrandContent
                                                } else {
                                                    ComposeAppTheme.colors.iconPrimary
                                                },
                                                background = if (manualOrderEnabled) ComposeAppTheme.colors.brandDefault
                                                else ComposeAppTheme.colors.buttonSecondaryFilledBackground,
                                                border = if (manualOrderEnabled) null
                                                else SecondaryButtonDefaults.filledBorder(),
                                            )
                                        }
                                        HSpacer(width = 12.dp)
                                        OptionController(
                                            uiState.period.titleResId,
                                            onOptionClick = {
                                                openPeriodSelector = true
                                            }
                                        )
                                        HSpacer(width = 12.dp)
                                        SignalButton(
                                            turnedOn = uiState.showSignal,
                                            onClick = {
                                                viewModel.onToggleSignal()
                                            })
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
        }
    }

    if (openSortingSelector) {
        AlertGroup(
            title = Res.string.Market_Sort_PopupTitle,
            select = Select(uiState.sortingField, viewModel.sortingOptions),
            onSelect = { selected ->
                manualOrderEnabled = false
                openSortingSelector = false
                scrollToTopAfterUpdate = true
                viewModel.onSelectSortingField(selected)
            },
            onDismiss = {
                openSortingSelector = false
            }
        )
    }
    if (openPeriodSelector) {
        AlertGroup(
            title = Res.string.CoinPage_Period,
            select = Select(uiState.period, viewModel.periods),
            onSelect = { selected ->
                openPeriodSelector = false
                scrollToTopAfterUpdate = true
                viewModel.onSelectPeriod(selected)
            },
            onDismiss = {
                openPeriodSelector = false
            }
        )
    }

}

@Composable
fun SignalButton(turnedOn: Boolean, onClick: () -> Unit) {
    val title = stringResource(Res.string.Market_Signals)
    if (turnedOn) {
        ButtonSecondaryYellow(
            title = title,
            onClick = onClick
        )
    } else {
        ButtonSecondaryDefault(
            title = title,
            onClick = onClick
        )
    }
}
