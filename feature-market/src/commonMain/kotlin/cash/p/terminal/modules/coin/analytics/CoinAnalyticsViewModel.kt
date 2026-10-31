package cash.p.terminal.modules.coin.analytics

import androidx.lifecycle.viewModelScope
import cash.p.terminal.resources.CoinAnalytics_ActiveAddresses
import cash.p.terminal.resources.CoinAnalytics_AttentionRequired
import cash.p.terminal.resources.CoinAnalytics_CexVolume
import cash.p.terminal.resources.CoinAnalytics_CountItems
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity
import cash.p.terminal.resources.CoinAnalytics_DexVolume
import cash.p.terminal.resources.CoinAnalytics_HighRiskItems
import cash.p.terminal.resources.CoinAnalytics_Holders
import cash.p.terminal.resources.CoinAnalytics_MediumRiskItems
import cash.p.terminal.resources.CoinAnalytics_OtherData
import cash.p.terminal.resources.CoinAnalytics_PoweredByDeFi
import cash.p.terminal.resources.CoinAnalytics_ProjectFee
import cash.p.terminal.resources.CoinAnalytics_ProjectRevenue
import cash.p.terminal.resources.CoinAnalytics_ProjectTvl
import cash.p.terminal.resources.CoinAnalytics_SmartContractAnalysis
import cash.p.terminal.resources.CoinAnalytics_TransactionCount
import cash.p.terminal.resources.Coin_Analytics_Current
import cash.p.terminal.resources.Coin_Analytics_Last30d
import cash.p.terminal.resources.TechnicalAdvice_Title
import cash.p.terminal.resources.CoinAnalytics_Funding
import cash.p.terminal.resources.CoinAnalytics_HoldersRank
import cash.p.terminal.resources.CoinAnalytics_Reports
import cash.p.terminal.resources.CoinAnalytics_Treasuries
import cash.p.terminal.resources.CoinAnalytics_TvlRatio
import cash.p.terminal.resources.Coin_Analytics_30DayRank
import cash.p.terminal.resources.Coin_Analytics_30DayUniqueAddress
import cash.p.terminal.resources.Coin_Analytics_30DayVolume
import cash.p.terminal.resources.Coin_Analytics_Audits
import cash.p.terminal.resources.Coin_Analytics_OverallScore
import cash.p.terminal.resources.Coin_Analytics_Rank
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.core.brandColor
import cash.p.terminal.core.order
import io.horizontalsystems.core.entities.Currency
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.ActionType
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.AnalyticChart
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.AnalyticInfo
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.AnalyticsViewItem
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.BlockViewItem
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.BoxItem.IconTitle
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.BoxItem.OverallScoreValue
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.BoxItem.Title
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.BoxItem.TitleWithInfo
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.BoxItem.Value
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.ChartViewItem
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.FooterType.FooterItem
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.RankType
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.ScoreCategory
import cash.p.terminal.modules.coin.audits.CoinAuditsModule
import cash.p.terminal.modules.coin.detectors.IssueItemParcelable
import cash.p.terminal.modules.coin.detectors.IssueParcelable
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.modules.metricchart.ProChartModule
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.TranslatableString.ResString
import cash.p.terminal.ui.compose.components.StackBarSlice
import io.horizontalsystems.chartview.ChartData
import io.horizontalsystems.chartview.ChartViewType
import cash.p.terminal.wallet.models.Analytics
import cash.p.terminal.wallet.models.BlockchainIssues
import cash.p.terminal.wallet.models.ChartPoint
import cash.p.terminal.wallet.entities.Coin
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

class CoinAnalyticsViewModel(
    private val service: CoinAnalyticsService,
    private val numberFormatter: IAppNumberFormatter,
    private val technicalAdviceViewItemFactory: TechnicalAdviceViewItemFactory,
    private val code: String,
    private val dispatcherProvider: DispatcherProvider
) : ViewModelUiState<CoinAnalyticsModule.UiState>() {

    private val currency = service.currency
    val coin: Coin
        get() = service.fullCoin.coin

    private var viewState: ViewState = ViewState.Loading
    private var analyticsViewItem: AnalyticsViewItem? = null
    private var cachedAnalyticData: Analytics? = null
    private var isRefreshing = false

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            service.stateFlow.collect {
                when (it) {
                    is DataState.Loading -> {
                        viewState = ViewState.Loading
                        emitState()
                    }

                    is DataState.Success -> {
                        viewState = ViewState.Success
                        cachedAnalyticData = it.data
                        analyticsViewItem = viewItem(it.data)
                        emitState()
                    }

                    is DataState.Error -> {
                        viewState = ViewState.Error(it.error)
                        emitState()
                    }
                }
            }
        }

        viewModelScope.launch(dispatcherProvider.io) {
            service.start()
        }
    }

    override fun createState() = CoinAnalyticsModule.UiState(
        viewState = viewState,
        viewItem = analyticsViewItem,
        isRefreshing = isRefreshing
    )

    fun refresh() {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                service.refresh()
            }
            isRefreshing = true
            emitState()
            delay(1000)
            isRefreshing = false
            emitState()
        }
    }

    private fun viewItem(analytics: Analytics): AnalyticsViewItem {
        val viewItems = getViewItems(analytics)
        if (viewItems.isNotEmpty()) {
            return AnalyticsViewItem.Analytics(viewItems)
        }

        return AnalyticsViewItem.NoData
    }

    private fun getViewItems(analytics: Analytics): List<BlockViewItem> {
        val blocks = mutableListOf<BlockViewItem>()

        analytics.technicalAdvice?.let { technicalAdvice ->
            val advice = technicalAdvice.advice ?: return@let
            blocks.add(
                BlockViewItem(
                    title = Res.string.TechnicalAdvice_Title,
                    info = AnalyticInfo.TechnicalIndicatorsInfo,
                    analyticChart = ChartViewItem(
                        AnalyticChart.TechAdvice(
                            CoinAnalyticsModule.TechAdviceData(
                                adviceTitle = advice.title,
                                detailText = technicalAdviceViewItemFactory.advice(technicalAdvice),
                                sliderPosition = advice.sliderIndex
                            )
                        ),
                        coin.uid,
                    ),
                    footerItems = emptyList()
                )
            )
        }

        analytics.cexVolume?.let { data ->
            val footerItems = mutableListOf<FooterItem>()
            data.rating?.let { rating ->
                getRatingFooterItem(rating, ScoreCategory.CexScoreCategory)?.let {
                    footerItems.add(it)
                }
            }
            footerItems.add(
                FooterItem(
                    title = Title(ResString(Res.string.Coin_Analytics_30DayRank)),
                    value = data.rank30d?.let { Value(getRank(it)) },
                    action = ActionType.OpenRank(RankType.CexVolumeRank)
                )
            )
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_CexVolume,
                    info = AnalyticInfo.CexVolumeInfo,
                    value = getFormattedSum(data.points.map { it.volume }, currency),
                    valuePeriod = getValuePeriod(false),
                    analyticChart = getChartViewItem(
                        data.chartPoints(), ChartViewType.Bar, ProChartModule.ChartType.CexVolume
                    ),
                    footerItems = footerItems
                )
            )
        }
        analytics.dexVolume?.let { data ->
            val footerItems = mutableListOf<FooterItem>()
            data.rating?.let { rating ->
                getRatingFooterItem(rating, ScoreCategory.DexVolumeScoreCategory)?.let {
                    footerItems.add(it)
                }
            }
            footerItems.add(
                FooterItem(
                    title = Title(ResString(Res.string.Coin_Analytics_30DayRank)),
                    value = data.rank30d?.let { Value(getRank(it)) },
                    action = ActionType.OpenRank(RankType.DexVolumeRank)
                )
            )
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_DexVolume,
                    info = AnalyticInfo.DexVolumeInfo,
                    value = getFormattedSum(data.points.map { it.volume }, currency),
                    valuePeriod = getValuePeriod(false),
                    analyticChart = getChartViewItem(
                        data.chartPoints(), ChartViewType.Bar, ProChartModule.ChartType.DexVolume
                    ),
                    footerItems = footerItems
                )
            )
        }
        analytics.tvl?.let { data ->
            val footerItems = mutableListOf<FooterItem>()
            data.ratio?.let {
                footerItems.add(
                    FooterItem(
                        title = Title(ResString(Res.string.Coin_Analytics_Rank)),
                        value = data.rank?.let { Value(getRank(it)) },
                        action = ActionType.OpenTvl
                    ),
                )
                footerItems.add(
                    FooterItem(
                        title = Title(ResString(Res.string.CoinAnalytics_TvlRatio)),
                        value = Value(numberFormatter.format(it, 2, 2))
                    )
                )
            }
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_ProjectTvl,
                    info = AnalyticInfo.TvlInfo,
                    value = getFormattedValue(data.points.last().tvl, currency),
                    valuePeriod = getValuePeriod(true),
                    analyticChart = getChartViewItem(
                        data.chartPoints(), ChartViewType.Line, ProChartModule.ChartType.Tvl
                    ),
                    footerItems = footerItems
                )
            )
        }

        analytics.dexLiquidity?.let { data ->
            val footerItems = mutableListOf<FooterItem>()
            data.rating?.let { rating ->
                getRatingFooterItem(rating, ScoreCategory.DexLiquidityScoreCategory)?.let {
                    footerItems.add(it)
                }
            }
            footerItems.add(
                FooterItem(
                    title = Title(ResString(Res.string.Coin_Analytics_Rank)),
                    value = data.rank?.let { Value(getRank(it)) },
                    action = ActionType.OpenRank(RankType.DexLiquidityRank)
                )
            )
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_DexLiquidity,
                    info = AnalyticInfo.DexLiquidityInfo,
                    value = getFormattedValue(data.points.last().volume, currency),
                    valuePeriod = getValuePeriod(true),
                    analyticChart = getChartViewItem(
                        data.chartPoints(), ChartViewType.Line, ProChartModule.ChartType.DexLiquidity
                    ),
                    footerItems = footerItems
                )
            )
        }
        analytics.addresses?.let { data ->
            val chartValue = formatNumberShort(data.points.last().count.toBigDecimal())
            val footerItems = mutableListOf<FooterItem>()
            data.rating?.let { rating ->
                getRatingFooterItem(rating, ScoreCategory.AddressesScoreCategory)?.let {
                    footerItems.add(it)
                }
            }
            footerItems.addAll(
                listOf(
                    FooterItem(
                        title = Title(ResString(Res.string.Coin_Analytics_30DayUniqueAddress)),
                        value = data.count30d?.let { Value(formatNumberShort(it.toBigDecimal())) },
                    ),
                    FooterItem(
                        title = Title(ResString(Res.string.Coin_Analytics_30DayRank)),
                        value = data.rank30d?.let { Value(getRank(it)) },
                        action = ActionType.OpenRank(RankType.AddressesRank)
                    )
                )
            )
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_ActiveAddresses,
                    info = AnalyticInfo.AddressesInfo,
                    value = chartValue,
                    valuePeriod = getValuePeriod(true),
                    analyticChart = getChartViewItem(
                        data.chartPoints(), ChartViewType.Line, ProChartModule.ChartType.AddressesCount
                    ),
                    footerItems = footerItems
                )
            )
        }
        analytics.transactions?.let { data ->
            val footerItems = mutableListOf<FooterItem>()
            data.rating?.let { rating ->
                getRatingFooterItem(rating, ScoreCategory.TransactionCountScoreCategory)?.let {
                    footerItems.add(it)
                }
            }
            footerItems.addAll(
                listOf(
                    FooterItem(
                        title = Title(ResString(Res.string.Coin_Analytics_30DayVolume)),
                        value = data.volume30d?.let { Value(getVolume(it)) }
                    ),
                    FooterItem(
                        title = Title(ResString(Res.string.Coin_Analytics_30DayRank)),
                        value = data.rank30d?.let { Value(getRank(it)) },
                        action = ActionType.OpenRank(RankType.TransactionCountRank)
                    ),
                )
            )
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_TransactionCount,
                    info = AnalyticInfo.TransactionCountInfo,
                    value = getFormattedSum(data.points.map { it.count.toBigDecimal() }),
                    valuePeriod = getValuePeriod(false),
                    analyticChart = getChartViewItem(
                        data.chartPoints(), ChartViewType.Bar, ProChartModule.ChartType.TxCount
                    ),
                    footerItems = footerItems
                )
            )
        }
        analytics.holders?.let { data ->
            val blockchains = service.blockchains(data.map { it.blockchainUid })
            val total = data.sumOf { it.holdersCount }
            val footerItems = mutableListOf<FooterItem>()
            val chartSlices = mutableListOf<StackBarSlice>()
            analytics.holdersRating?.let { holdersRating ->
                getRatingFooterItem(holdersRating, ScoreCategory.HoldersScoreCategory)?.let {
                    footerItems.add(it)
                }
            }
            data.sortedByDescending { it.holdersCount }.forEach { item ->
                val blockchain = blockchains.firstOrNull { it.uid == item.blockchainUid }
                blockchain?.let {
                    val percent = item.holdersCount.divide(total, 4, RoundingMode.HALF_EVEN)
                        .times("100".toBigDecimal())
                    val percentFormatted = numberFormatter.format(percent, 0, 2, suffix = "%")
                    chartSlices.add(
                        StackBarSlice(
                            value = percent.toFloat(),
                            color = blockchain.type.brandColor
                        )
                    )
                    footerItems.add(
                        FooterItem(
                            title = IconTitle(
                                ImageSource.Remote(
                                    blockchain.type.imageUrl,
                                    Res.drawable.coin_placeholder
                                ),
                                TranslatableString.PlainString(blockchain.name)
                            ),
                            value = Value(percentFormatted),
                            action = ActionType.OpenTokenHolders(coin, blockchain)
                        )
                    )
                }
            }
            analytics.holdersRank?.let { holdersRank ->
                footerItems.add(
                    FooterItem(
                        title = Title(ResString(Res.string.CoinAnalytics_HoldersRank)),
                        value = Value(getRank(holdersRank)),
                        action = ActionType.OpenRank(RankType.HoldersRank)
                    ),
                )
            }
            if (footerItems.isEmpty()) {
                return@let
            }

            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_Holders,
                    info = AnalyticInfo.HoldersInfo,
                    value = getFormattedSum(listOf(total)),
                    valuePeriod = getValuePeriod(true),
                    analyticChart = ChartViewItem(AnalyticChart.StackedBars(chartSlices), coin.uid),
                    footerItems = footerItems
                )
            )
        }
        analytics.fee?.let { data ->
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_ProjectFee,
                    info = null,
                    value = data.value30d?.let { getFormattedSum(listOf(it), currency) },
                    valuePeriod = getValuePeriod(false),
                    analyticChart = null,
                    sectionDescription = data.description,
                    footerItems = listOf(
                        FooterItem(
                            title = Title(ResString(Res.string.Coin_Analytics_30DayRank)),
                            value = data.rank30d?.let { Value(getRank(it)) },
                            action = ActionType.OpenRank(RankType.FeeRank)
                        ),
                    )
                )
            )
        }
        analytics.revenue?.let { data ->
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_ProjectRevenue,
                    info = null,
                    value = data.value30d?.let { getFormattedSum(listOf(it), currency) },
                    valuePeriod = getValuePeriod(false),
                    analyticChart = null,
                    sectionDescription = data.description,
                    footerItems = listOf(
                        FooterItem(
                            title = Title(ResString(Res.string.Coin_Analytics_30DayRank)),
                            value = data.rank30d?.let { Value(getRank(it)) },
                            action = ActionType.OpenRank(RankType.RevenueRank)
                        ),
                    )
                )
            )
        }
        analytics.issues?.let { issues ->
            val detectorFooterItems = mutableListOf<CoinAnalyticsModule.FooterType.DetectorFooterItem>()

            val sortedList = issues.mapNotNull {
                val blockchain = service.blockchain(it.blockchain) ?: return@mapNotNull null
                CoinAnalyticsModule.BlockchainAndIssues(blockchain, it)
            }.sortedBy { it.blockchain.type.order }

            sortedList.forEach { blockchainAndIssues ->
                val blockchain = blockchainAndIssues.blockchain
                val blockchainTitle = blockchain.name
                val icon =
                    ImageSource.Remote(blockchain.type.imageUrl, Res.drawable.coin_placeholder)
                val blockchainIssues = blockchainAndIssues.issues
                detectorFooterItems.add(
                    CoinAnalyticsModule.FooterType.DetectorFooterItem(
                        title = IconTitle(
                            icon,
                            TranslatableString.PlainString(blockchainTitle)
                        ),
                        value = Value(
                            Translator.getString(
                                Res.string.CoinAnalytics_CountItems,
                                blockchainIssues.issues.size
                            )
                        ),
                        action = ActionType.OpenDetectorsDetails(
                            title = blockchainTitle,
                            issues = blockchainIssues.issues.map {
                                IssueParcelable(
                                    issue = it.issue,
                                    title = it.title,
                                    description = it.description,
                                    issues = it.issues?.map { issueItem ->
                                        IssueItemParcelable(
                                            impact = issueItem.impact,
                                            confidence = issueItem.confidence,
                                            description = issueItem.description.trim()
                                        )
                                    }
                                )
                            }
                        ),
                        issues = getIssueSnippet(blockchainIssues.issues)
                    )
                )
            }
            blocks.add(
                BlockViewItem(
                    title = Res.string.CoinAnalytics_SmartContractAnalysis,
                    info = null,
                    analyticChart = null,
                    footerItems = detectorFooterItems,
                    sectionDescription = Translator.getString(
                        Res.string.CoinAnalytics_PoweredByDeFi
                    )
                )
            )
        }

        val hasFundamentalData = listOf(
            analytics.reports,
            analytics.fundsInvested,
            analytics.treasuries,
            analytics.audits,
        ).any { it != null }
        if (hasFundamentalData) {
            val footerItems = mutableListOf<FooterItem>()
            analytics.reports?.let { reportsCount ->
                footerItems.add(
                    FooterItem(
                        title = Title(ResString(Res.string.CoinAnalytics_Reports)),
                        value = Value(reportsCount.toString()),
                        action = ActionType.OpenReports(coin.uid)
                    )
                )
            }
            analytics.fundsInvested?.let { invested ->
                footerItems.add(
                    FooterItem(
                        title = Title(ResString(Res.string.CoinAnalytics_Funding)),
                        value = Value(getFormattedValue(invested, currency)),
                        action = ActionType.OpenInvestors(coin.uid)
                    )
                )
            }
            analytics.treasuries?.let { treasuries ->
                footerItems.add(
                    FooterItem(
                        title = Title(ResString(Res.string.CoinAnalytics_Treasuries)),
                        value = Value(getFormattedValue(treasuries, currency)),
                        action = ActionType.OpenTreasuries(coin)
                    )
                )
            }

            analytics.audits?.let { audits ->
                val auditsParcelable = audits.map {
                    CoinAuditsModule.AuditParcelable(
                        date = it.date,
                        name = it.name,
                        auditUrl = it.auditUrl,
                        techIssues = it.techIssues,
                        partnerName = it.partnerName
                    )
                }
                footerItems.add(
                    FooterItem(
                        title = Title(ResString(Res.string.Coin_Analytics_Audits)),
                        action = ActionType.OpenAudits(auditsParcelable)
                    )
                )
            }

            blocks.add(
                BlockViewItem(
                    title = null,
                    info = null,
                    analyticChart = null,
                    footerItems = footerItems,
                    sectionTitle = Res.string.CoinAnalytics_OtherData,
                    showFooterDivider = false,
                )
            )
        }

        return blocks
    }

    private fun getIssueSnippet(issues: List<BlockchainIssues.Issue>): List<CoinAnalyticsModule.IssueSnippet> {
        var high = 0
        var medium = 0
        var low = 0
        issues.forEach { issue ->
            var innerHigh = 0
            var innerMedium = 0
            var innerLow = 0
            issue.issues?.forEach { issueItem ->
                when (issueItem.impact) {
                    "Critical",
                    "High" -> innerHigh++

                    "Medium" -> innerMedium++
                    "Low",
                    "Informational" -> innerLow++

                    "Optimization" -> { /* ignore */
                    }
                }
            }
            if (innerHigh > 0) {
                high++
            } else if (innerMedium > 0) {
                medium++
            } else if (innerLow > 0) {
                low++
            }
        }
        val snippets = mutableListOf<CoinAnalyticsModule.IssueSnippet>()
        if (high > 0) {
            snippets.add(
                issueSnippet(high, CoinAnalyticsModule.IssueType.High)
            )
        }
        if (medium > 0) {
            snippets.add(
                issueSnippet(medium, CoinAnalyticsModule.IssueType.Medium)
            )
        }
        if (low > 0) {
            snippets.add(
                issueSnippet(low, CoinAnalyticsModule.IssueType.Attention)
            )
        }

        return snippets
    }

    private fun issueSnippet(
        count: Int,
        type: CoinAnalyticsModule.IssueType
    ): CoinAnalyticsModule.IssueSnippet {
        val title = when (type) {
            CoinAnalyticsModule.IssueType.High -> Res.string.CoinAnalytics_HighRiskItems
            CoinAnalyticsModule.IssueType.Medium -> Res.string.CoinAnalytics_MediumRiskItems
            CoinAnalyticsModule.IssueType.Attention -> Res.string.CoinAnalytics_AttentionRequired
        }
        return CoinAnalyticsModule.IssueSnippet(
            title = title,
            count = count.toString(),
            type = type
        )
    }

    private fun getRatingFooterItem(ratingString: String?, scoreCategory: ScoreCategory): FooterItem? {
        return CoinAnalyticsModule.OverallScore.fromString(ratingString)?.let { rating ->
            FooterItem(
                title = TitleWithInfo(
                    ResString(Res.string.Coin_Analytics_OverallScore), ActionType.OpenOverallScoreInfo(scoreCategory)
                ),
                value = OverallScoreValue(rating),
            )
        }
    }

    private fun getFormattedSum(values: List<BigDecimal>, currency: Currency? = null): String {
        return currency?.let {
            numberFormatter.formatFiatShort(values.sumOf { it }, currency.symbol, 2)
        } ?: numberFormatter.formatCoinShort(values.sumOf { it }, null, 0)
    }

    private fun getFormattedValue(value: BigDecimal, currency: Currency? = null): String {
        return currency?.let {
            numberFormatter.formatFiatShort(value, currency.symbol, 2)
        } ?: numberFormatter.formatCoinShort(value, null, 0)
    }

    private fun getVolume(volume30d: BigDecimal): String {
        val formatted = numberFormatter.formatNumberShort(volume30d, 1)
        return "$formatted $code"
    }

    private fun formatNumberShort(number: BigDecimal): String {
        return numberFormatter.formatNumberShort(number, 1)
    }

    private fun getValuePeriod(isMovement: Boolean): String {
        return Translator.getString(
            if (isMovement) Res.string.Coin_Analytics_Current else Res.string.Coin_Analytics_Last30d
        )
    }

    private fun getRank(rank: Int) = "#$rank"

    private fun getChartViewItem(
        values: List<ChartPoint>,
        chartViewType: ChartViewType,
        chartType: ProChartModule.ChartType?,
    ): ChartViewItem? {
        if (values.isEmpty()) return null

        val points = values.map {
            io.horizontalsystems.chartview.models.ChartPoint(it.value.toFloat(), it.timestamp)
        }

        val chartData = ChartData(points, chartViewType == ChartViewType.Line, false)

        val analyticChart = when (chartViewType) {
            ChartViewType.Bar -> AnalyticChart.Bars(chartData)
            ChartViewType.Line -> AnalyticChart.Line(chartData)
        }

        return ChartViewItem(analyticChart, coin.uid, chartType)
    }

}
