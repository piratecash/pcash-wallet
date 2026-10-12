package cash.p.terminal.modules.market.tvl

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import cash.p.terminal.resources.ic_arrow_down_20
import cash.p.terminal.resources.ic_arrow_up_20
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.ic_percent_20
import cash.p.terminal.resources.ic_usd_20
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_platform_placeholder_24
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.CoinFragmentInput
import io.horizontalsystems.chartview.ui.Chart
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.market.MarketDataValue
import io.horizontalsystems.core.entities.Value
import cash.p.terminal.modules.market.tvl.TvlModule.SelectorDialogState
import cash.p.terminal.modules.market.tvl.TvlModule.TvlDiffType
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.ButtonSecondaryWithIcon
import cash.p.terminal.ui.compose.components.DescriptionCard
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderSorting
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui.compose.components.MarketCoinFirstRow
import cash.p.terminal.ui.compose.components.MarketCoinSecondRow
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.SectionItemBorderedRowUniversalClear
import cash.p.terminal.ui.compose.hsRememberLazyListState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import org.jetbrains.compose.resources.DrawableResource
import cash.p.terminal.resources.MarketGlobalMetrics_ChainSelectorTitle
import cash.p.terminal.resources.MarketGlobalMetrics_NoCoin
import cash.p.terminal.resources.Market_TVL
import cash.p.terminal.resources.SyncError
import androidx.compose.runtime.remember

class TvlPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val koin = getKoin()
        val globalMarketRepository = remember { koin.get<GlobalMarketRepository>() }
        val tvlChartViewModel = koinViewModel<TvlChartViewModel> { parametersOf(globalMarketRepository) }
        val viewModel = koinViewModel<TvlViewModel> { parametersOf(globalMarketRepository) }
        val hudMessenger = LocalHudMessenger.current
        val noCoinMessage = stringResource(Res.string.MarketGlobalMetrics_NoCoin)

        TvlScreen(viewModel, tvlChartViewModel, navigation) { coinUid ->
            if (coinUid != null) {
                navigation.slideFromRight(CoinPage(CoinFragmentInput(coinUid)))
            } else {
                hudMessenger.showWarning(noCoinMessage)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvlScreen(
    tvlViewModel: TvlViewModel,
    chartViewModel: TvlChartViewModel,
    navigation: HSNavigation,
    onCoinClick: (String?) -> Unit
) {
    val uiState = tvlViewModel.uiState
    val viewState = uiState.viewState.merge(chartViewModel.uiState.viewState)
    val tvlData = uiState.tvlData
    val tvlDiffType = uiState.tvlDiffType

    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)) {
        AppBar(
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(Res.string.Button_Close),
                    icon = Res.drawable.ic_close_24,
                    onClick = {
                        navigation.navigateUpSafely()
                    }
                )
            )
        )

        HSSwipeRefresh(
            refreshing = uiState.isRefreshing,
            onRefresh = {
                tvlViewModel.refresh()
            }
        ) {
            Crossfade(viewState, label = "") { viewState ->
                when (viewState) {
                    ViewState.Loading -> {
                        Loading()
                    }

                    is ViewState.Error -> {
                        ListErrorView(stringResource(Res.string.SyncError), tvlViewModel::onErrorClick)
                    }

                    ViewState.Success -> {
                        val listState = hsRememberLazyListState(
                            2,
                            tvlData?.chainSelect?.selected,
                            tvlData?.sortDescending
                        )

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                        ) {
                            item {
                                tvlViewModel.header.let { header ->
                                    DescriptionCard(header.title, header.description, header.icon)
                                }
                            }
                            item {
                                Chart(
                                    getSelectedPointCallback = chartViewModel::getSelectedPoint,
                                    uiState = chartViewModel.uiState
                                ) {
                                    chartViewModel.onSelectChartInterval(it)
                                    tvlViewModel.onSelectChartInterval(it)
                                }
                            }

                            tvlData?.let { tvlData ->
                                stickyHeader {
                                    TvlMenu(
                                        tvlData.chainSelect,
                                        tvlData.sortDescending,
                                        tvlDiffType,
                                        tvlViewModel::onClickChainSelector,
                                        tvlViewModel::onToggleSortType,
                                        tvlViewModel::onToggleTvlDiffType
                                    )
                                }

                                items(tvlData.coinTvlViewItems) { item ->
                                    DefiMarket(
                                        item.name,
                                        item.chain,
                                        item.iconUrl,
                                        item.iconPlaceholder,
                                        item.tvl,
                                        when (tvlDiffType) {
                                            TvlDiffType.Percent -> item.tvlChangePercent?.let {
                                                MarketDataValue.DiffNew(Value.Percent(item.tvlChangePercent))
                                            }

                                            TvlDiffType.Currency -> item.tvlChangeAmount?.let {
                                                MarketDataValue.DiffNew(Value.Currency(item.tvlChangeAmount))
                                            }
                                        },
                                        item.rank
                                    ) { onCoinClick(item.coinUid) }
                                }
                            }
                        }
                    }

                    null -> {}
                }
            }
            // chain selector dialog
            when (val option = uiState.chainSelectorDialogState) {
                is SelectorDialogState.Opened -> {
                    AlertGroup(
                        title = Res.string.MarketGlobalMetrics_ChainSelectorTitle,
                        select = option.select,
                        onSelect = {
                            chartViewModel.onSelectChain(it)
                            tvlViewModel.onSelectChain(it)
                        },
                        onDismiss = tvlViewModel::onChainSelectorDialogDismiss
                    )
                }

                SelectorDialogState.Closed -> {}
            }
        }
    }
}

@Composable
private fun TvlMenu(
    chainSelect: Select<TvlModule.Chain>,
    sortDescending: Boolean,
    tvlDiffType: TvlDiffType,
    onClickChainSelector: () -> Unit,
    onToggleSortType: () -> Unit,
    onToggleTvlDiffType: () -> Unit
) {
    HeaderSorting(borderBottom = true, borderTop = true) {
        HSpacer(16.dp)
        ButtonSecondaryWithIcon(
            modifier = Modifier.height(28.dp),
            onClick = onClickChainSelector,
            title = chainSelect.selected.title.getString(),
            iconRight = painterResource(Res.drawable.ic_down_arrow_20),
        )
        HSpacer(8.dp)
        ButtonSecondaryWithIcon(
            title = stringResource(Res.string.Market_TVL),
            iconRight = painterResource(
                if (sortDescending) Res.drawable.ic_arrow_down_20 else Res.drawable.ic_arrow_up_20
            ),
            onClick = onToggleSortType
        )
        HSpacer(8.dp)
        ButtonSecondaryCircle(
            modifier = Modifier.padding(end = 16.dp),
            icon = if (tvlDiffType == TvlDiffType.Percent) Res.drawable.ic_percent_20 else Res.drawable.ic_usd_20,
            onClick = onToggleTvlDiffType
        )
        HSpacer(width = 16.dp)
    }
}

@Composable
private fun DefiMarket(
    name: String,
    chain: TranslatableString,
    iconUrl: String,
    iconPlaceholder: DrawableResource?,
    tvl: CurrencyValue,
    marketDataValue: MarketDataValue?,
    label: String? = null,
    onClick: (() -> Unit)? = null
) {
    SectionItemBorderedRowUniversalClear(
        onClick = onClick,
        borderBottom = true
    ) {
        Image(
            painter = rememberAsyncImagePainterWithFallback(
                model = iconUrl,
                error = painterResource(
                    iconPlaceholder ?: Res.drawable.ic_platform_placeholder_24
                )
            ),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            MarketCoinFirstRow(name, tvl.getFormattedShort())
            Spacer(modifier = Modifier.height(3.dp))
            MarketCoinSecondRow(chain.getString(), marketDataValue, label)
        }
    }
}
