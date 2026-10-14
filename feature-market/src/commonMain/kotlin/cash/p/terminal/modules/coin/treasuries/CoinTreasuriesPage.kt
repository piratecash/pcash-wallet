package cash.p.terminal.modules.coin.treasuries

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.CoinPage_Treasuries
import cash.p.terminal.resources.CoinPage_Treasuries_PoweredBy
import cash.p.terminal.resources.SyncError
import cash.p.terminal.resources.ic_sort_h2l_20
import cash.p.terminal.resources.ic_sort_l2h_20
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellFooter
import cash.p.terminal.ui_compose.components.HeaderSorting
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui.compose.components.MarketCoinFirstRow
import cash.p.terminal.ui_compose.components.SectionItemBorderedRowUniversalClear
import cash.p.terminal.ui.compose.components.SortMenu
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_brand
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.resources.Res
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.resources.CoinPage_Treasuries_FilterTitle

class CoinTreasuriesPage(val input: Coin) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        CoinTreasuriesScreen(
            koinViewModel { parametersOf(input) },
            navigation,
        )
    }
}

@Composable
private fun CoinTreasuriesScreen(
    viewModel: CoinTreasuriesViewModel,
    navigation: HSNavigation,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val treasuriesData by viewModel.coinTreasuries.collectAsStateWithLifecycle()
    val chainSelectorDialogState by viewModel.treasuryTypeSelectorDialogState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.CoinPage_Treasuries),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                }
            )
        }
    ) { paddingValues ->
        HSSwipeRefresh(
            refreshing = isRefreshing,
            modifier = Modifier.padding(paddingValues),
            onRefresh = {
                viewModel.refresh()
            },
            content = {
                Crossfade(viewState, label = "") { viewState ->
                    when (viewState) {
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
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                treasuriesData?.let { treasuriesData ->
                                    item {
                                        CoinTreasuriesMenu(
                                            treasuryTypeSelect = treasuriesData.treasuryTypeSelect,
                                            sortDescending = treasuriesData.sortDescending,
                                            onClickTreasuryTypeSelector = viewModel::onClickTreasuryTypeSelector,
                                            onToggleSortType = viewModel::onToggleSortType
                                        )
                                    }

                                    items(treasuriesData.coinTreasuries) { item ->
                                        SectionItemBorderedRowUniversalClear(
                                            borderBottom = true
                                        ) {
                                            HsImage(
                                                url = item.fundLogoUrl,
                                                modifier = Modifier
                                                    .padding(end = 16.dp)
                                                    .size(32.dp)
                                            )
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                MarketCoinFirstRow(item.fund, item.amount)
                                                VSpacer(3.dp)
                                                CoinTreasurySecondRow(
                                                    item.country,
                                                    item.amountInCurrency
                                                )
                                            }
                                        }
                                    }

                                    item {
                                        VSpacer(32.dp)
                                        CellFooter(
                                            text = stringResource(Res.string.CoinPage_Treasuries_PoweredBy)
                                        )
                                    }
                                }
                            }
                        }

                    }
                }

                // chain selector dialog
                when (val option = chainSelectorDialogState) {
                    is CoinTreasuriesModule.SelectorDialogState.Opened -> {
                        AlertGroup(
                            Res.string.CoinPage_Treasuries_FilterTitle,
                            option.select,
                            viewModel::onSelectTreasuryType,
                            viewModel::onTreasuryTypeSelectorDialogDismiss
                        )
                    }

                    CoinTreasuriesModule.SelectorDialogState.Closed -> Unit
                }
            }
        )
    }
}

@Composable
private fun CoinTreasuriesMenu(
    treasuryTypeSelect: Select<CoinTreasuriesModule.TreasuryTypeFilter>,
    sortDescending: Boolean,
    onClickTreasuryTypeSelector: () -> Unit,
    onToggleSortType: () -> Unit
) {
    HeaderSorting(borderTop = true, borderBottom = true) {
        Box(modifier = Modifier.weight(1f)) {
            SortMenu(treasuryTypeSelect.selected.title, onClickTreasuryTypeSelector)
        }
        ButtonSecondaryCircle(
            modifier = Modifier.padding(end = 16.dp),
            icon = if (sortDescending) Res.drawable.ic_sort_h2l_20 else Res.drawable.ic_sort_l2h_20,
            onClick = { onToggleSortType() }
        )
    }
}

@Composable
private fun CoinTreasurySecondRow(
    country: String,
    fiatAmount: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        subhead2_grey(
            text = country,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        subhead2_brand(
            text = fiatAmount,
            maxLines = 1,
        )
    }
}
