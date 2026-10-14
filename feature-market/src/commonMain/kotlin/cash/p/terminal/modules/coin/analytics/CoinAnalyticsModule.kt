package cash.p.terminal.modules.coin.analytics

import cash.p.terminal.resources.CoinAnalytics_ActiveAddresses
import cash.p.terminal.resources.CoinAnalytics_ActiveAddressesRank
import cash.p.terminal.resources.CoinAnalytics_ActiveAddressesRank_Description
import cash.p.terminal.resources.CoinAnalytics_CexVolume
import cash.p.terminal.resources.CoinAnalytics_CexVolumeRank
import cash.p.terminal.resources.CoinAnalytics_CexVolumeRank_Description
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity
import cash.p.terminal.resources.CoinAnalytics_DexLiquidityRank
import cash.p.terminal.resources.CoinAnalytics_DexLiquidityRank_Description
import cash.p.terminal.resources.CoinAnalytics_DexVolume
import cash.p.terminal.resources.CoinAnalytics_DexVolumeRank
import cash.p.terminal.resources.CoinAnalytics_DexVolumeRank_Description
import cash.p.terminal.resources.CoinAnalytics_Holders
import cash.p.terminal.resources.CoinAnalytics_HoldersRank
import cash.p.terminal.resources.CoinAnalytics_HoldersRank_Description
import cash.p.terminal.resources.CoinAnalytics_ProjectFeeRank
import cash.p.terminal.resources.CoinAnalytics_ProjectFeeRank_Description
import cash.p.terminal.resources.CoinAnalytics_ProjectRevenueRank
import cash.p.terminal.resources.CoinAnalytics_ProjectRevenueRank_Description
import cash.p.terminal.resources.CoinAnalytics_ProjectTvl
import cash.p.terminal.resources.CoinAnalytics_ProjectTvl_FullTitle
import cash.p.terminal.resources.CoinAnalytics_TransactionCount
import cash.p.terminal.resources.CoinAnalytics_TransactionCountRank
import cash.p.terminal.resources.Coin_Analytics_OverallScore_ActiveAddresses
import cash.p.terminal.resources.Coin_Analytics_OverallScore_CexVolume
import cash.p.terminal.resources.Coin_Analytics_OverallScore_DexLiquidity
import cash.p.terminal.resources.Coin_Analytics_OverallScore_DexVolume
import cash.p.terminal.resources.Coin_Analytics_OverallScore_Excellent
import cash.p.terminal.resources.Coin_Analytics_OverallScore_Fair
import cash.p.terminal.resources.Coin_Analytics_OverallScore_Good
import cash.p.terminal.resources.Coin_Analytics_OverallScore_Holders
import cash.p.terminal.resources.Coin_Analytics_OverallScore_Poor
import cash.p.terminal.resources.Coin_Analytics_OverallScore_ProjectTvl
import cash.p.terminal.resources.Coin_Analytics_OverallScore_TransactionCount
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TechnicalAdvice_InfoTitle
import cash.p.terminal.resources.ic_score_excellent_24
import cash.p.terminal.resources.ic_score_fair_24
import cash.p.terminal.resources.ic_score_good_24
import cash.p.terminal.resources.ic_score_poor_24
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.audits.CoinAuditsModule
import cash.p.terminal.modules.coin.detectors.IssueParcelable
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.modules.metricchart.ProChartModule
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.StackBarSlice
import io.horizontalsystems.chartview.ChartData
import io.horizontalsystems.chartview.models.ChartPoint
import io.horizontalsystems.core.entities.Blockchain
import cash.p.terminal.wallet.models.BlockchainIssues
import cash.p.terminal.wallet.entities.Coin
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

object CoinAnalyticsModule {

    data class BlockViewItem(
        val title: StringResource?,
        val info: AnalyticInfo?,
        val value: String? = null,
        val valuePeriod: String? = null,
        val analyticChart: ChartViewItem?,
        val footerItems: List<FooterType>,
        val sectionTitle: StringResource? = null,
        val sectionDescription: String? = null,
        val showFooterDivider: Boolean = true,
    )


    sealed class FooterType {
        class FooterItem(
            val title: BoxItem,
            val value: BoxItem? = null,
            val action: ActionType? = null
        ) : FooterType()

        class DetectorFooterItem(
            val title: BoxItem,
            val value: BoxItem? = null,
            val action: ActionType? = null,
            val issues: List<IssueSnippet>
        ) : FooterType()
    }

    class IssueSnippet(
        val title: StringResource,
        val count: String,
        val type: IssueType
    )

    enum class IssueType {
        High, Medium, Attention
    }

    data class ChartViewItem(
        val analyticChart: AnalyticChart,
        val coinUid: String,
        val chartType: ProChartModule.ChartType? = null
    )

    sealed class AnalyticChart {
        class Line(val data: ChartData) : AnalyticChart()
        class Bars(val data: ChartData) : AnalyticChart()
        class StackedBars(val data: List<StackBarSlice>) : AnalyticChart()
        class TechAdvice(val data: TechAdviceData) : AnalyticChart()
    }

    data class TechAdviceData(
        val adviceTitle: String,
        val detailText: String,
        val sliderPosition: Int
    )

    enum class OverallScore(val title: StringResource, val icon: DrawableResource) {
        Excellent(Res.string.Coin_Analytics_OverallScore_Excellent, Res.drawable.ic_score_excellent_24),
        Good(Res.string.Coin_Analytics_OverallScore_Good, Res.drawable.ic_score_good_24),
        Fair(Res.string.Coin_Analytics_OverallScore_Fair, Res.drawable.ic_score_fair_24),
        Poor(Res.string.Coin_Analytics_OverallScore_Poor, Res.drawable.ic_score_poor_24);

        companion object {
            fun fromString(value: String?): OverallScore? = when (value) {
                "excellent" -> Excellent
                "good" -> Good
                "fair" -> Fair
                "poor" -> Poor
                else -> null
            }
        }
    }

    enum class ScoreCategory(val title: StringResource, val description: StringResource) {
        CexScoreCategory(Res.string.CoinAnalytics_CexVolume, Res.string.Coin_Analytics_OverallScore_CexVolume),
        DexVolumeScoreCategory(Res.string.CoinAnalytics_DexVolume, Res.string.Coin_Analytics_OverallScore_DexVolume),
        DexLiquidityScoreCategory(
            Res.string.CoinAnalytics_DexLiquidity, Res.string.Coin_Analytics_OverallScore_DexLiquidity
        ),
        AddressesScoreCategory(
            Res.string.CoinAnalytics_ActiveAddresses, Res.string.Coin_Analytics_OverallScore_ActiveAddresses
        ),
        TransactionCountScoreCategory(
            Res.string.CoinAnalytics_TransactionCount, Res.string.Coin_Analytics_OverallScore_TransactionCount
        ),
        HoldersScoreCategory(Res.string.CoinAnalytics_Holders, Res.string.Coin_Analytics_OverallScore_Holders),
        TvlScoreCategory(Res.string.CoinAnalytics_ProjectTvl, Res.string.Coin_Analytics_OverallScore_ProjectTvl),
    }

    sealed class BoxItem {
        class Title(val text: TranslatableString) : BoxItem()
        class TitleWithInfo(val text: TranslatableString, val action: ActionType) : BoxItem()
        class IconTitle(val image: ImageSource, val text: TranslatableString) : BoxItem()
        class Value(val text: String) : BoxItem()
        class OverallScoreValue(val score: OverallScore) : BoxItem()
        object Dots : BoxItem()
    }

    data class PreviewBlockViewItem(
        val title: StringResource?,
        val info: AnalyticInfo?,
        val chartType: PreviewChartType?,
        val footerItems: List<FooterType>,
        val sectionTitle: StringResource? = null,
        val showValueDots: Boolean = true,
        val showFooterDivider: Boolean = true,
    )

    enum class PreviewChartType {
        Line, Bars, StackedBars
    }

    enum class AnalyticInfo(val title: StringResource) {
        CexVolumeInfo(Res.string.CoinAnalytics_CexVolume),
        DexVolumeInfo(Res.string.CoinAnalytics_DexVolume),
        DexLiquidityInfo(Res.string.CoinAnalytics_DexLiquidity),
        AddressesInfo(Res.string.CoinAnalytics_ActiveAddresses),
        TransactionCountInfo(Res.string.CoinAnalytics_TransactionCount),
        HoldersInfo(Res.string.CoinAnalytics_Holders),
        TvlInfo(Res.string.CoinAnalytics_ProjectTvl_FullTitle),
        TechnicalIndicatorsInfo(Res.string.TechnicalAdvice_InfoTitle),
    }

    enum class RankType(
        val title: StringResource,
        val description: StringResource,
        val headerIconName: String
    ) {
        CexVolumeRank(
            Res.string.CoinAnalytics_CexVolumeRank, Res.string.CoinAnalytics_CexVolumeRank_Description, "cex_volume"
        ),
        DexVolumeRank(
            Res.string.CoinAnalytics_DexVolumeRank, Res.string.CoinAnalytics_DexVolumeRank_Description, "dex_volume"
        ),
        DexLiquidityRank(
            Res.string.CoinAnalytics_DexLiquidityRank, Res.string.CoinAnalytics_DexLiquidityRank_Description,
            "dex_liquidity"
        ),
        AddressesRank(
            Res.string.CoinAnalytics_ActiveAddressesRank, Res.string.CoinAnalytics_ActiveAddressesRank_Description,
            "active_addresses"
        ),
        TransactionCountRank(
            Res.string.CoinAnalytics_TransactionCountRank, Res.string.CoinAnalytics_TransactionCountRank, "trx_count"
        ),
        RevenueRank(
            Res.string.CoinAnalytics_ProjectRevenueRank,
            Res.string.CoinAnalytics_ProjectRevenueRank_Description,
            "revenue"
        ),
        FeeRank(Res.string.CoinAnalytics_ProjectFeeRank, Res.string.CoinAnalytics_ProjectFeeRank_Description, "fee"),
        HoldersRank(Res.string.CoinAnalytics_HoldersRank, Res.string.CoinAnalytics_HoldersRank_Description, "holders");

        val headerIcon: ImageSource
            get() = ImageSource.Remote("https://cdn.blocksdecoded.com/header-images/$headerIconName@3x.png")
    }

    data class UiState(
        val viewState: ViewState,
        val viewItem: AnalyticsViewItem?,
        val isRefreshing: Boolean
    )

    sealed class AnalyticsViewItem {
        class Preview(val blocks: List<PreviewBlockViewItem>) : AnalyticsViewItem()
        class Analytics(val blocks: List<BlockViewItem>) : AnalyticsViewItem()
        object NoData : AnalyticsViewItem()
    }

    sealed class ActionType {
        object Preview : ActionType()
        object OpenTvl : ActionType()
        class OpenOverallScoreInfo(val scoreCategory: ScoreCategory) : ActionType()
        class OpenRank(val type: RankType) : ActionType()
        class OpenReports(val coinUid: String) : ActionType()
        class OpenInvestors(val coinUid: String) : ActionType()
        class OpenTreasuries(val coin: Coin) : ActionType()
        class OpenAudits(val audits: List<CoinAuditsModule.AuditParcelable>) : ActionType()
        class OpenTokenHolders(val coin: Coin, val blockchain: Blockchain) : ActionType()
        class OpenDetectorsDetails(val issues: List<IssueParcelable>, val title: String) : ActionType()
    }

    fun zigzagPlaceholderAnalyticChart(isMovementChart: Boolean): AnalyticChart {
        val chartItems = mutableListOf<ChartPoint>()

        var lastTimeStamp = 0L
        for (i in 1..8) {
            val baseTimeStamp = (i * 100).toLong()
            val baseValue = i * 2

            chartItems.addAll(
                listOf(
                    ChartPoint((baseValue + 2).toFloat(), (baseTimeStamp)),
                    ChartPoint((baseValue + 6).toFloat(), (baseTimeStamp + 25)),
                    ChartPoint((baseValue).toFloat(), (baseTimeStamp + 50)),
                    ChartPoint((baseValue + 9).toFloat(), (baseTimeStamp + 75)),
                )
            )

            lastTimeStamp = baseTimeStamp + (75 + 25)
        }

        chartItems.add(ChartPoint(16f, lastTimeStamp))

        val chartData = ChartData(chartItems, isMovementChart, true)

        return if (isMovementChart) AnalyticChart.Line(chartData) else AnalyticChart.Bars(chartData)
    }

    data class BlockchainAndIssues(val blockchain: Blockchain, val issues: BlockchainIssues)
}
