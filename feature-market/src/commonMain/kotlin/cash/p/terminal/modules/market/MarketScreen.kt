package cash.p.terminal.modules.market

import cash.p.terminal.resources.MarketGlobalMetrics_EtfInflow
import cash.p.terminal.resources.MarketGlobalMetrics_TotalMarketCap
import cash.p.terminal.resources.MarketGlobalMetrics_TvlInDefi
import cash.p.terminal.resources.MarketGlobalMetrics_Volume
import cash.p.terminal.resources.Market_Title
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.modules.market.MarketModule.Tab
import cash.p.terminal.modules.market.etf.EtfPage
import cash.p.terminal.modules.market.favorites.MarketFavoritesScreen
import cash.p.terminal.modules.market.filters.MarketFiltersPage
import cash.p.terminal.modules.market.metricspage.MetricsPage
import cash.p.terminal.modules.market.posts.MarketPostsScreen
import cash.p.terminal.modules.market.search.MarketSearchPage
import cash.p.terminal.modules.market.topcoins.TopCoins
import cash.p.terminal.modules.market.toppairs.TopPairsScreen
import cash.p.terminal.modules.market.topplatforms.TopPlatforms
import cash.p.terminal.modules.market.tvl.TvlPage
import cash.p.terminal.modules.metricchart.MetricsType
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Market_Filters
import cash.p.terminal.resources.Market_Search
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_manage_2_24
import cash.p.terminal.resources.icon_search
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.ScrollableTabs
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_bran
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.components.caption_remus
import cash.p.terminal.ui_compose.components.micro_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.models.MarketGlobal
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import java.math.BigDecimal
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MarketScreen(navigation: HSNavigation, paddingValuesParent: PaddingValues) {
    val viewModel = koinViewModel<MarketViewModel>()
    val uiState = viewModel.uiState
    val tabs = viewModel.tabs

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Market_Title),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Market_Search),
                        icon = Res.drawable.icon_search,
                        tint = ComposeAppTheme.colors.brandDefault,
                        onClick = {
                            navigation.slideFromRight(MarketSearchPage())
                        },
                    ),
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Market_Filters),
                        icon = Res.drawable.ic_manage_2_24,
                        onClick = {
                            navigation.slideFromRight(MarketFiltersPage())
                        },
                    ),
                )
            )
        }
    ) {
        Column(
            Modifier
                .padding(
                    top = it.calculateTopPadding(),
                    bottom = paddingValuesParent.calculateBottomPadding()
                )
                .background(ComposeAppTheme.colors.backgroundBase)
        ) {
            Crossfade(uiState.marketGlobal, label = "") {
                MetricsBoard(navigation, it, uiState.currency)
            }
            HorizontalDivider(
                color = ComposeAppTheme.colors.borderDivider,
                thickness = 1.dp
            )
            TabsSection(navigation, tabs, uiState.selectedTab) { tab ->
                viewModel.onSelect(tab)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabsSection(
    navigation: HSNavigation,
    tabs: Array<Tab>,
    selectedTab: Tab,
    onTabClick: (Tab) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { tabs.size }

    LaunchedEffect(key1 = selectedTab, block = {
        pagerState.scrollToPage(selectedTab.ordinal)
    })
    val tabItems = tabs.map {
        TabItem(TranslatableString.ResString(id = it.titleResId).getString(), it == selectedTab, it)
    }

    ScrollableTabs(tabItems) {
        onTabClick(it)
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false
    ) { page ->
        when (tabs[page]) {
            Tab.Coins -> {
                TopCoins(onCoinClick = { onCoinClick(it, navigation) })
            }

            Tab.Watchlist -> {
                MarketFavoritesScreen(navigation)
            }

            Tab.Posts -> {
                MarketPostsScreen()
            }

            Tab.Platform -> {
                TopPlatforms(navigation)
            }

            Tab.Pairs -> {
                TopPairsScreen()
            }
        }
    }
}

private fun IAppNumberFormatter.formatFiatShortened(value: BigDecimal, symbol: String): String {
    return formatFiatShort(value, symbol, 2)
}

private fun IAppNumberFormatter.getDiff(it: BigDecimal): String {
    val sign = if (it >= BigDecimal.ZERO) "+" else "-"
    return format(it.abs(), 0, 2, sign, "%")
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricsBoard(
    navigation: HSNavigation,
    marketGlobal: MarketGlobal?,
    currency: Currency
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.surfacePrimary)
    ) {
        MarketTotalCard(
            title = stringResource(Res.string.MarketGlobalMetrics_TotalMarketCap),
            value = marketGlobal?.marketCap,
            change = marketGlobal?.marketCapChange,
            currency = currency,
            onClick = {
                openMetricsPage(MetricsType.TotalMarketCap, navigation)
            }
        )

        VDivider()

        MarketTotalCard(
            title = stringResource(Res.string.MarketGlobalMetrics_Volume),
            value = marketGlobal?.volume,
            change = marketGlobal?.volumeChange,
            currency = currency,
            onClick = {
                openMetricsPage(MetricsType.Volume24h, navigation)
            }
        )

        VDivider()

        MarketTotalCard(
            title = stringResource(Res.string.MarketGlobalMetrics_TvlInDefi),
            value = marketGlobal?.tvl,
            change = marketGlobal?.tvlChange,
            currency = currency,
            onClick = {
                openMetricsPage(MetricsType.TvlInDefi, navigation)
            }
        )

        VDivider()

        MarketTotalCard(
            title = stringResource(Res.string.MarketGlobalMetrics_EtfInflow),
            value = marketGlobal?.etfTotalInflow,
            change = marketGlobal?.etfDailyInflow,
            currency = currency,
            onClick = {
                openMetricsPage(MetricsType.Etf, navigation)
            }
        )
    }
}

@Composable
private fun VDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(color = ComposeAppTheme.colors.borderDivider)
    )
}

@Composable
private fun RowScope.MarketTotalCard(
    title: String,
    value: BigDecimal?,
    change: BigDecimal?,
    currency: Currency,
    onClick: () -> Unit,
) {
    val numberFormatter = koinInject<IAppNumberFormatter>()
    val changeStr = change?.let { numberFormatter.getDiff(it) }
    val changePositive = change?.let { it > BigDecimal.ZERO }
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        micro_grey(
            text = title,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
        VSpacer(4.dp)
        caption_bran(
            text = value?.let { numberFormatter.formatFiatShortened(it, currency.symbol) } ?: "---",
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
        VSpacer(4.dp)
        if (changePositive == null) {
            caption_grey(
                text = changeStr ?: "---",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        } else if (changePositive) {
            caption_remus(
                text = changeStr ?: "---",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        } else {
            caption_lucian(
                text = changeStr ?: "---",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }
}

private fun openMetricsPage(metricsType: MetricsType, navigation: HSNavigation) {
    when (metricsType) {
        MetricsType.TvlInDefi -> {
            navigation.slideFromBottom(TvlPage())
        }

        MetricsType.Etf -> {
            navigation.slideFromBottom(EtfPage())
        }

        else -> {
            navigation.slideFromBottom(MetricsPage(metricsType))
        }
    }
}

private fun onCoinClick(coinUid: String, navigation: HSNavigation) {
    navigation.slideFromRight(CoinPage(CoinFragmentInput(coinUid)))
}
