package cash.p.terminal.modules.market.overview

import androidx.compose.runtime.Immutable
import cash.p.terminal.modules.market.MarketModule
import cash.p.terminal.modules.market.MarketViewItem
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.modules.market.TopMarket
import cash.p.terminal.modules.market.search.MarketSearchModule.DiscoveryItem.Category
import cash.p.terminal.modules.market.topnftcollections.TopNftCollectionViewItem
import cash.p.terminal.modules.market.topplatforms.TopPlatformViewItem
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui.extensions.MetricData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.models.TopPair
import io.horizontalsystems.core.IAppNumberFormatter
import java.math.BigDecimal
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

object MarketOverviewModule {

    data class UiState(
        val viewState: ViewState,
        val viewItem: ViewItem?,
        val isRefreshing: Boolean
    )

    @Immutable
    data class ViewItem(
        val marketMetrics: MarketMetrics,
//        val boards: List<Board>,
        val topNftCollectionsBoard: TopNftCollectionsBoard,
        val topSectorsBoard: TopSectorsBoard,
        val topPlatformsBoard: TopPlatformsBoard,
        val topMarketPairs: List<TopPairViewItem>,
    )

    data class MarketMetrics(
        val totalMarketCap: MetricData,
        val volume24h: MetricData,
        val defiCap: MetricData,
        val defiTvl: MetricData,
    ) {
        operator fun get(page: Int) = when (page) {
            0 -> totalMarketCap
            1 -> volume24h
            2 -> defiCap
            3 -> defiTvl
            else -> throw  IndexOutOfBoundsException()
        }
    }

    data class MarketMetricsPoint(
        val value: BigDecimal,
        val timestamp: Long
    )

    data class Board(
        val boardHeader: BoardHeader,
        val marketViewItems: List<MarketViewItem>,
        val type: MarketModule.ListType
    )

    data class BoardHeader(
        val title: StringResource,
        val iconRes: DrawableResource,
        val topMarketSelect: Select<TopMarket>
    )

    data class TopNftCollectionsBoard(
        val title: StringResource,
        val iconRes: DrawableResource,
        val timeDurationSelect: Select<TimeDuration>,
        val collections: List<TopNftCollectionViewItem>
    )

    data class TopSectorsBoard(
        val title: StringResource,
        val iconRes: DrawableResource,
        val items: List<Category>
    )

    data class TopPlatformsBoard(
        val title: StringResource,
        val iconRes: DrawableResource,
        val timeDurationSelect: Select<TimeDuration>,
        val items: List<TopPlatformViewItem>
    )

}

data class TopPairViewItem(
    val title: String,
    val rank: String,
    val name: String,
    val base: String,
    val baseCoinUid: String?,
    val baseCoin: Coin?,
    val target: String,
    val targetCoinUid: String?,
    val targetCoin: Coin?,
    val iconUrl: String?,
    val tradeUrl: String?,
    val volume: BigDecimal,
    val volumeInFiat: String,
    val price: String?
) {
    companion object {
        fun createFromTopPair(
            topPair: TopPair,
            currencySymbol: String,
            numberFormatter: IAppNumberFormatter
        ): TopPairViewItem {
            val volumeStr = numberFormatter.formatFiatShort(topPair.volume, currencySymbol, 2)

            val priceStr = topPair.price?.let {
                numberFormatter.formatCoinShort(
                    it,
                    topPair.target,
                    8
                )
            }

            return TopPairViewItem(
                title = "${topPair.base}/${topPair.target}",
                name = topPair.marketName ?: "",
                base = topPair.base,
                baseCoinUid = topPair.baseCoinUid,
                baseCoin = topPair.baseCoin,
                target = topPair.target,
                targetCoinUid = topPair.targetCoinUid,
                targetCoin = topPair.targetCoin,
                iconUrl = topPair.marketLogo,
                rank = topPair.rank.toString(),
                tradeUrl = topPair.tradeUrl,
                volume = topPair.volume,
                volumeInFiat = volumeStr,
                price = priceStr,
            )
        }
    }
}
