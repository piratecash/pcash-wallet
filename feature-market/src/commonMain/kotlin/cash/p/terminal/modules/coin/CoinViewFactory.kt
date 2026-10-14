package cash.p.terminal.modules.coin

import cash.p.terminal.core.urlDisplayHostOrNull
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import cash.p.terminal.modules.coin.overview.CoinOverviewItem
import cash.p.terminal.modules.coin.overview.CoinOverviewViewItem
import cash.p.terminal.resources.CoinPage_DilutedMarketCap
import cash.p.terminal.resources.CoinPage_Github
import cash.p.terminal.resources.CoinPage_Guide
import cash.p.terminal.resources.CoinPage_LaunchDate
import cash.p.terminal.resources.CoinPage_MarketCap
import cash.p.terminal.resources.CoinPage_Reddit
import cash.p.terminal.resources.CoinPage_Telegram
import cash.p.terminal.resources.CoinPage_TotalSupply
import cash.p.terminal.resources.CoinPage_TradingVolume
import cash.p.terminal.resources.CoinPage_Tvl
import cash.p.terminal.resources.CoinPage_Twitter
import cash.p.terminal.resources.CoinPage_Website
import cash.p.terminal.resources.CoinPage_Whitepaper
import cash.p.terminal.resources.CoinPage_inCirculation
import cash.p.terminal.resources.ic_academy_20
import cash.p.terminal.resources.ic_clipboard_20
import cash.p.terminal.resources.ic_github_20
import cash.p.terminal.resources.ic_globe_20
import cash.p.terminal.resources.ic_reddit_20
import cash.p.terminal.resources.ic_telegram_20
import cash.p.terminal.resources.ic_twitter_20
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.models.HsTimePeriod
import cash.p.terminal.wallet.models.LinkType
import cash.p.terminal.wallet.models.MarketInfoOverview
import cash.p.terminal.wallet.models.TokenHolders
import org.jetbrains.compose.resources.DrawableResource
import java.math.BigDecimal

data class MarketTickerViewItem(
    val market: String,
    val marketImageUrl: String?,
    val pair: String,
    val volumeFiat: String,
    val volumeToken: String,
    val tradeUrl: String?,
    val badge: TranslatableString?
)

sealed class RoiViewItem {
    class HeaderRowViewItem(
        val title: String,
        val periods: List<HsTimePeriod>,
    ) : RoiViewItem()

    class RowViewItem(
        val title: String,
        val values: List<BigDecimal?>,
    ) : RoiViewItem()
}

open class ContractInfo(
    val rawValue: String,
    val imgUrl: String,
    val explorerUrl: String?,
    val name: String? = null,
    val schema: String? = null
) {
    val shortened = rawValue.shorten()
}

data class CoinDataItem(
    val title: String,
    val value: String? = null,
    val valueLabeled: String? = null,
    val valueLabeledBackground: Int? = null,
    val valueDecorated: Boolean = false,
    val icon: DrawableResource? = null,
    val rankLabel: String? = null
)

class MajorHolderItem(
    val index: Int,
    val address: String,
    val balance: String,
    val sharePercent: String
)

data class CoinLink(
    val url: String,
    val linkType: LinkType,
    val title: String,
    val icon: DrawableResource,
)

class CoinViewFactory(
    private val currency: Currency,
    private val numberFormatter: IAppNumberFormatter
) {

    fun getRoi(performance: Map<String, Map<HsTimePeriod, BigDecimal>>): List<RoiViewItem> {
        val rows = mutableListOf<RoiViewItem>()

        val timePeriods = performance.map { it.value.keys }.flatten().distinct()
        rows.add(RoiViewItem.HeaderRowViewItem("ROI", timePeriods))
        performance.forEach { (vsCurrency, performanceVsCurrency) ->
            if (performanceVsCurrency.isNotEmpty()) {
                val values = timePeriods.map { performanceVsCurrency[it] }
                rows.add(RoiViewItem.RowViewItem("vs ${vsCurrency.uppercase()}", values))
            }
        }

        return rows
    }

    fun getOverviewViewItem(item: CoinOverviewItem): CoinOverviewViewItem {
        val overview = item.marketInfoOverview

        return CoinOverviewViewItem(
            roi = getRoi(overview.performance),
            links = getLinks(overview, item.guideUrl),
            about = overview.description,
            marketData = getMarketItems(item),
            marketCapRank = item.marketInfoOverview.marketCapRank
        )
    }

    fun getTop10Share(number: BigDecimal): String =
        numberFormatter.format(number, 0, 2, suffix = "%")

    fun getHoldersCount(number: BigDecimal): String = numberFormatter.formatNumberShort(number, 2)

    fun getCoinMajorHolders(tokenHolders: TokenHolders): List<MajorHolderItem> {
        val list = mutableListOf<MajorHolderItem>()
        if (tokenHolders.topHolders.isEmpty()) {
            return list
        }

        tokenHolders.topHolders
            .sortedByDescending { it.percentage }
            .forEachIndexed { index, holder ->
                val shareFormatted = numberFormatter.format(holder.percentage, 0, 4, suffix = "%")
                val balanceFormatted = numberFormatter.formatNumberShort(holder.balance, 2)
                list.add(
                    MajorHolderItem(
                        index + 1,
                        holder.address,
                        balanceFormatted,
                        shareFormatted
                    )
                )
            }

        return list
    }

    private fun getMarketItems(
        item: CoinOverviewItem,
    ): MutableList<CoinDataItem> {
        val overview = item.marketInfoOverview
        val items = mutableListOf<CoinDataItem>()
        overview.marketCap?.let {
            val marketCapString = formatFiatShortened(it, currency.symbol)
            val marketCapRankString = overview.marketCapRank?.let { "#$it" }
            items.add(
                CoinDataItem(
                    Translator.getString(Res.string.CoinPage_MarketCap),
                    marketCapString,
                    rankLabel = marketCapRankString
                )
            )
        }
        overview.totalSupply?.let {
            val totalSupplyString = numberFormatter.formatCoinShort(
                it,
                item.coinCode,
                8
            )
            items.add(
                CoinDataItem(
                    Translator.getString(Res.string.CoinPage_TotalSupply),
                    totalSupplyString
                )
            )
        }
        overview.circulatingSupply?.let {
            val supplyString = numberFormatter.formatCoinShort(
                it,
                item.coinCode,
                8
            )
            items.add(
                CoinDataItem(
                    Translator.getString(Res.string.CoinPage_inCirculation),
                    supplyString
                )
            )
        }

        overview.volume24h?.let {
            val volumeString = formatFiatShortened(it, currency.symbol)
            items.add(
                CoinDataItem(
                    Translator.getString(Res.string.CoinPage_TradingVolume),
                    volumeString
                )
            )
        }

        overview.tvl?.let {
            val tvlString = formatFiatShortened(it, currency.symbol)
            items.add(
                CoinDataItem(
                    Translator.getString(Res.string.CoinPage_Tvl),
                    tvlString
                )
            )
        }

        overview.dilutedMarketCap?.let {
            val dilutedMarketCapString = formatFiatShortened(it, currency.symbol)
            items.add(
                CoinDataItem(
                    Translator.getString(Res.string.CoinPage_DilutedMarketCap),
                    dilutedMarketCapString
                )
            )
        }

        overview.genesisDate?.let {
            val genesisDateString = DateHelper.formatDate(it, "MMM d, yyyy")
            items.add(
                CoinDataItem(
                    Translator.getString(Res.string.CoinPage_LaunchDate),
                    genesisDateString
                )
            )
        }

        return items
    }

    fun getLinks(coinMarketDetails: MarketInfoOverview, guideUrl: String?): List<CoinLink> {
        val linkTypes = listOf(
            LinkType.Guide,
            LinkType.Website,
            LinkType.Whitepaper,
            LinkType.Reddit,
            LinkType.Twitter,
            LinkType.Telegram,
            LinkType.Github,
        )

        val links = linkTypes.mapNotNull { linkType ->
            if (linkType == LinkType.Guide) {
                guideUrl?.let {
                    CoinLink(guideUrl, linkType, getTitle(linkType, guideUrl), getIcon(linkType))
                }
            } else {
                coinMarketDetails.links[linkType]?.let { link ->
                    val trimmed = link.trim()
                    if (trimmed.isNotBlank()) {
                        CoinLink(trimmed, linkType, getTitle(linkType, trimmed), getIcon(linkType))
                    } else {
                        null
                    }
                }
            }
        }

        return links
    }

    private fun getIcon(linkType: LinkType): DrawableResource {
        return when (linkType) {
            LinkType.Guide -> Res.drawable.ic_academy_20
            LinkType.Website -> Res.drawable.ic_globe_20
            LinkType.Whitepaper -> Res.drawable.ic_clipboard_20
            LinkType.Twitter -> Res.drawable.ic_twitter_20
            LinkType.Telegram -> Res.drawable.ic_telegram_20
            LinkType.Reddit -> Res.drawable.ic_reddit_20
            LinkType.Github -> Res.drawable.ic_github_20
        }
    }

    private fun getTitle(linkType: LinkType, link: String? = null): String {
        return when (linkType) {
            LinkType.Guide -> Translator.getString(Res.string.CoinPage_Guide)
            LinkType.Website -> {
                link?.urlDisplayHostOrNull()
                    ?: Translator.getString(Res.string.CoinPage_Website)
            }

            LinkType.Whitepaper -> Translator.getString(Res.string.CoinPage_Whitepaper)
            LinkType.Twitter -> {
                link?.split("/")?.lastOrNull()?.replaceFirst("@", "")?.let { "@$it" }
                    ?: Translator.getString(Res.string.CoinPage_Twitter)
            }

            LinkType.Telegram -> Translator.getString(Res.string.CoinPage_Telegram)
            LinkType.Reddit -> Translator.getString(Res.string.CoinPage_Reddit)
            LinkType.Github -> Translator.getString(Res.string.CoinPage_Github)
        }
    }

    private fun formatFiatShortened(value: BigDecimal, symbol: String): String {
        return numberFormatter.formatFiatShort(value, symbol, 2)
    }

}
