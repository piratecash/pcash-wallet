package cash.p.terminal.modules.coin.analytics

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.AnalyticsViewItem
import cash.p.terminal.modules.coin.analytics.ui.AnalyticsBlockHeader
import cash.p.terminal.modules.coin.analytics.ui.AnalyticsChart
import cash.p.terminal.modules.coin.analytics.ui.AnalyticsContainer
import cash.p.terminal.modules.coin.analytics.ui.AnalyticsContentNumber
import cash.p.terminal.modules.coin.analytics.ui.AnalyticsFooterCell
import cash.p.terminal.modules.coin.audits.CoinAuditsPage
import cash.p.terminal.modules.coin.detectors.DetectorsPage
import cash.p.terminal.modules.coin.investments.CoinInvestmentsPage
import cash.p.terminal.modules.coin.majorholders.CoinMajorHoldersPage
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.coin.ranks.CoinRankPage
import cash.p.terminal.modules.coin.reports.CoinReportsPage
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesPage
import cash.p.terminal.modules.info.CoinAnalyticsInfoPage
import cash.p.terminal.modules.info.OverallScoreInfoPage
import cash.p.terminal.modules.market.tvl.TvlPage
import cash.p.terminal.modules.metricchart.ProChartSheet
import cash.p.terminal.modules.subscription.SubscriptionInfoPage
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui.compose.components.StackBarSlice
import cash.p.terminal.ui.compose.components.StackedBarChart
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_jacob
import cash.p.terminal.ui_compose.components.subhead1_lucian
import cash.p.terminal.ui_compose.components.subhead1_remus
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.wallet.entities.FullCoin

@Composable
fun CoinAnalyticsScreen(
    fullCoin: FullCoin,
    navigation: HSNavigation,
) {
    val viewModel =
        viewModel<CoinAnalyticsViewModel>(factory = CoinAnalyticsModule.Factory(fullCoin))
    val uiState = viewModel.uiState

    HSSwipeRefresh(
        refreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
    ) {
        Crossfade(uiState.viewState, label = "") { viewState ->
            when (viewState) {
                ViewState.Loading -> {
                    Loading()
                }

                ViewState.Success -> {
                    when (val item = uiState.viewItem) {
                        AnalyticsViewItem.NoData -> {
                            ListEmptyView(
                                text = stringResource(R.string.CoinAnalytics_ProjectNoAnalyticData),
                                icon = R.drawable.ic_not_available
                            )
                        }

                        is AnalyticsViewItem.Preview -> {
                            AnalyticsDataPreview(
                                previewBlocks = item.blocks,
                                navigation = navigation
                            )
                        }

                        is AnalyticsViewItem.Analytics -> {
                            AnalyticsData(
                                item.blocks,
                                navigation,
                            )
                        }

                        null -> {

                        }
                    }
                }

                is ViewState.Error -> {
                    ListErrorView(stringResource(R.string.SyncError), viewModel::refresh)
                }
            }
        }
    }
}

@Composable
private fun AnalyticsData(
    blocks: List<CoinAnalyticsModule.BlockViewItem>,
    navigation: HSNavigation,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(blocks) { block ->
            AnalyticsBlock(
                block,
                navigation,
            )
        }
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AnalyticsDataPreview(
    previewBlocks: List<CoinAnalyticsModule.PreviewBlockViewItem>,
    navigation: HSNavigation,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(previewBlocks) { block ->
            AnalyticsPreviewBlock(block, navigation)
        }
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AnalyticsBlock(
    block: CoinAnalyticsModule.BlockViewItem,
    navigation: HSNavigation,
) {
    AnalyticsContainer(
        showFooterDivider = block.showFooterDivider,
        sectionTitle = block.sectionTitle?.let {
            {
                body_leah(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(it)
                )
            }
        },
        titleRow = {
            block.title?.let {
                AnalyticsBlockHeader(
                    title = stringResource(it),
                    onInfoClick = block.info?.let { info ->
                        {
                            navigation.slideFromRight(CoinAnalyticsInfoPage(info))
                        }
                    }
                )
            }
        },
        sectionDescription = {
            block.sectionDescription?.let {
                InfoText(it)
            }
        },
        bottomRows = {
            block.footerItems.forEachIndexed { index, item ->
                FooterCell(item, index, navigation)
            }
        }
    ) {
        Column(
            modifier = Modifier.clickable(
                enabled = block.analyticChart?.chartType != null,
                onClick = {
                    val coinUid = block.analyticChart?.coinUid
                    val chartType = block.analyticChart?.chartType
                    if (coinUid != null && chartType != null) {
                        navigation.slideFromBottom(
                            ProChartSheet(
                                ProChartSheet.Input(
                                    coinUid,
                                    Translator.getString(chartType.titleRes),
                                    chartType.ordinal,
                                )
                            )
                        )
                    }
                }
            )
        ) {
            block.value?.let {
                AnalyticsContentNumber(number = it, period = block.valuePeriod)
            }
            block.analyticChart?.let { chartViewItem ->
                VSpacer(12.dp)
                AnalyticsChart(chartViewItem.analyticChart)
            }
        }
    }
}

@Composable
private fun FooterCell(
    item: CoinAnalyticsModule.FooterType,
    index: Int,
    navigation: HSNavigation
) {
    when (item) {
        is CoinAnalyticsModule.FooterType.FooterItem -> {
            AnalyticsFooterCell(
                title = item.title,
                value = item.value,
                showTopDivider = index != 0,
                showRightArrow = item.action != null,
                cellAction = item.action,
                onActionClick = { action ->
                    handleActionClick(action, navigation)
                }
            )
        }

        is CoinAnalyticsModule.FooterType.DetectorFooterItem -> {
            Column(
                modifier = Modifier.clickable {
                    item.action?.let { handleActionClick(it, navigation) }
                }
            ) {
                AnalyticsFooterCell(
                    title = item.title,
                    value = item.value,
                    showTopDivider = index != 0,
                    showRightArrow = item.action != null,
                    cellAction = null,
                    onActionClick = {}
                )

                if (item.issues.isNotEmpty()) {
                    item.issues.forEach { snippet ->
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            subhead2_grey(
                                text = stringResource(snippet.title),
                                modifier = Modifier.weight(1f)
                            )
                            when (snippet.type) {
                                CoinAnalyticsModule.IssueType.High -> {
                                    subhead1_lucian(text = snippet.count)
                                }

                                CoinAnalyticsModule.IssueType.Medium -> {
                                    subhead1_jacob(text = snippet.count)
                                }

                                CoinAnalyticsModule.IssueType.Attention -> {
                                    subhead1_remus(text = snippet.count)
                                }
                            }
                        }
                    }
                    VSpacer(12.dp)
                }
            }
        }
    }
}

@Composable
private fun AnalyticsPreviewBlock(
    block: CoinAnalyticsModule.PreviewBlockViewItem,
    navigation: HSNavigation
) {
    AnalyticsContainer(
        showFooterDivider = block.showFooterDivider,
        sectionTitle = block.sectionTitle?.let {
            {
                body_leah(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(it)
                )
            }
        },
        titleRow = {
            block.title?.let {
                AnalyticsBlockHeader(
                    title = stringResource(it),
                    onInfoClick = block.info?.let { info ->
                        {
                            navigation.slideFromRight(CoinAnalyticsInfoPage(info))
                        }
                    }
                )
            }
        },
        bottomRows = {
            block.footerItems.forEachIndexed { index, item ->
                FooterCell(item, index, navigation)
            }
        }
    ) {
        if (block.showValueDots) {
            AnalyticsContentNumber(
                number = stringResource(R.string.CoinAnalytics_ThreeDots),
            )
        }
        block.chartType?.let { chartType ->
            VSpacer(12.dp)
            if (chartType == CoinAnalyticsModule.PreviewChartType.StackedBars) {
                val lockedSlices = listOf(
                    StackBarSlice(value = 50.34f, color = Color(0xBF808085)),
                    StackBarSlice(value = 37.75f, color = Color(0x80808085)),
                    StackBarSlice(value = 11.9f, color = Color(0x40808085)),
                )
                StackedBarChart(lockedSlices, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                AnalyticsChart(
                    CoinAnalyticsModule.zigzagPlaceholderAnalyticChart(
                        chartType == CoinAnalyticsModule.PreviewChartType.Line
                    ),
                )
            }
        }
        if (block.showValueDots || block.chartType != null) {
            VSpacer(12.dp)
        }
    }
}

private fun handleActionClick(
    action: CoinAnalyticsModule.ActionType,
    navigation: HSNavigation
) {
    when (action) {
        is CoinAnalyticsModule.ActionType.OpenTokenHolders -> {
            navigation.slideFromBottom(
                CoinMajorHoldersPage(CoinMajorHoldersPage.Input(action.coin.uid, action.blockchain))
            )
        }

        is CoinAnalyticsModule.ActionType.OpenAudits -> {
            navigation.slideFromRight(CoinAuditsPage(CoinAuditsPage.Input(action.audits)))
        }

        is CoinAnalyticsModule.ActionType.OpenTreasuries -> {
            navigation.slideFromRight(CoinTreasuriesPage(action.coin))
        }

        is CoinAnalyticsModule.ActionType.OpenReports -> {
            navigation.slideFromRight(CoinReportsPage(CoinReportsPage.Input(action.coinUid)))
        }

        is CoinAnalyticsModule.ActionType.OpenInvestors -> {
            navigation.slideFromRight(CoinInvestmentsPage(CoinInvestmentsPage.Input(action.coinUid)))
        }

        is CoinAnalyticsModule.ActionType.OpenRank -> {
            navigation.slideFromBottom(CoinRankPage(action.type))
        }

        is CoinAnalyticsModule.ActionType.OpenOverallScoreInfo -> {
            navigation.slideFromRight(OverallScoreInfoPage(action.scoreCategory))
        }

        CoinAnalyticsModule.ActionType.OpenTvl -> {
            navigation.slideFromBottom(TvlPage())
        }

        CoinAnalyticsModule.ActionType.Preview -> {
            navigation.slideFromBottom(SubscriptionInfoPage())
        }

        is CoinAnalyticsModule.ActionType.OpenDetectorsDetails -> {
            navigation.slideFromRight(DetectorsPage(DetectorsPage.Input(action.title, action.issues)))
        }
    }
}
