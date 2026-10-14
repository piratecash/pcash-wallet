package cash.p.terminal.modules.market.filters

import cash.p.terminal.modules.market.MarketItem
import cash.p.terminal.resources.Market_Filter_Buy
import cash.p.terminal.resources.Market_Filter_ChooseSet
import cash.p.terminal.resources.Market_Filter_MarketCap
import cash.p.terminal.resources.Market_Filter_Neutral
import cash.p.terminal.resources.Market_Filter_PriceChange
import cash.p.terminal.resources.Market_Filter_PriceChange_Negative_10_minus
import cash.p.terminal.resources.Market_Filter_PriceChange_Negative_25_minus
import cash.p.terminal.resources.Market_Filter_PriceChange_Negative_50_minus
import cash.p.terminal.resources.Market_Filter_PriceChange_Negative_75_minus
import cash.p.terminal.resources.Market_Filter_PriceChange_Positive_100_plus
import cash.p.terminal.resources.Market_Filter_PriceChange_Positive_10_plus
import cash.p.terminal.resources.Market_Filter_PriceChange_Positive_25_plus
import cash.p.terminal.resources.Market_Filter_PriceChange_Positive_50_plus
import cash.p.terminal.resources.Market_Filter_PricePeriod
import cash.p.terminal.resources.Market_Filter_Range_0_10M
import cash.p.terminal.resources.Market_Filter_Range_0_500M
import cash.p.terminal.resources.Market_Filter_Range_0_50M
import cash.p.terminal.resources.Market_Filter_Range_0_5M
import cash.p.terminal.resources.Market_Filter_Range_100B_500B
import cash.p.terminal.resources.Market_Filter_Range_100M_1B
import cash.p.terminal.resources.Market_Filter_Range_10B_100B
import cash.p.terminal.resources.Market_Filter_Range_10B_50B
import cash.p.terminal.resources.Market_Filter_Range_10B_More
import cash.p.terminal.resources.Market_Filter_Range_10M_40M
import cash.p.terminal.resources.Market_Filter_Range_1B_10B
import cash.p.terminal.resources.Market_Filter_Range_1B_5B
import cash.p.terminal.resources.Market_Filter_Range_200M_1B
import cash.p.terminal.resources.Market_Filter_Range_200M_2B
import cash.p.terminal.resources.Market_Filter_Range_20M_100M
import cash.p.terminal.resources.Market_Filter_Range_2B_10B
import cash.p.terminal.resources.Market_Filter_Range_40M_200M
import cash.p.terminal.resources.Market_Filter_Range_500B_More
import cash.p.terminal.resources.Market_Filter_Range_500M_2B
import cash.p.terminal.resources.Market_Filter_Range_50B_More
import cash.p.terminal.resources.Market_Filter_Range_50M_200M
import cash.p.terminal.resources.Market_Filter_Range_5B_More
import cash.p.terminal.resources.Market_Filter_Range_5M_20M
import cash.p.terminal.resources.Market_Filter_RiskToTrade
import cash.p.terminal.resources.Market_Filter_Sell
import cash.p.terminal.resources.Market_Filter_StrongBuy
import cash.p.terminal.resources.Market_Filter_StrongSell
import cash.p.terminal.resources.Market_Filter_TimePeriod_1D
import cash.p.terminal.resources.Market_Filter_TimePeriod_1M
import cash.p.terminal.resources.Market_Filter_TimePeriod_1W
import cash.p.terminal.resources.Market_Filter_TimePeriod_1Y
import cash.p.terminal.resources.Market_Filter_TimePeriod_2W
import cash.p.terminal.resources.Market_Filter_TimePeriod_3M
import cash.p.terminal.resources.Market_Filter_TimePeriod_6M
import cash.p.terminal.resources.Market_Filter_Top_100
import cash.p.terminal.resources.Market_Filter_Top_1000
import cash.p.terminal.resources.Market_Filter_Top_1500
import cash.p.terminal.resources.Market_Filter_Top_250
import cash.p.terminal.resources.Market_Filter_Top_500
import cash.p.terminal.resources.Market_Filter_TradingSignals
import cash.p.terminal.resources.Market_Filter_Volume
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import cash.p.terminal.wallet.models.Analytics.TechnicalAdvice.Advice
import io.horizontalsystems.core.entities.Blockchain
import io.reactivex.Single
import org.jetbrains.compose.resources.StringResource

object MarketFiltersModule {
    enum class FilterDropdown(val titleResId: StringResource){
        CoinSet(Res.string.Market_Filter_ChooseSet),
        MarketCap(Res.string.Market_Filter_MarketCap),
        TradingVolume(Res.string.Market_Filter_Volume),
        PriceChange(Res.string.Market_Filter_PriceChange),
        PricePeriod(Res.string.Market_Filter_PricePeriod),
        TradingSignals(Res.string.Market_Filter_TradingSignals),
    }

    data class BlockchainViewItem(val blockchain: Blockchain, val checked: Boolean)
}

enum class FilterTradingSignal(val titleResId: StringResource) {
    StrongBuy(Res.string.Market_Filter_StrongBuy),
    Buy(Res.string.Market_Filter_Buy),
    Neutral(Res.string.Market_Filter_Neutral),
    Sell(Res.string.Market_Filter_Sell),
    StrongSell(Res.string.Market_Filter_StrongSell),
    CriticalTrade(Res.string.Market_Filter_RiskToTrade);

    fun getAdvices(): List<Advice> {
        return when (this) {
            StrongSell -> listOf(Advice.StrongSell)
            Sell -> listOf(Advice.Sell)
            Neutral -> listOf(Advice.Neutral)
            Buy -> listOf(Advice.Buy)
            StrongBuy -> listOf(Advice.StrongBuy)
            CriticalTrade -> listOf(Advice.Overbought, Advice.Oversold)
        }
    }
}

enum class CoinList(val itemsCount: Int, val titleResId: StringResource) {
    Top100(100, Res.string.Market_Filter_Top_100),
    Top250(250, Res.string.Market_Filter_Top_250),
    Top500(500, Res.string.Market_Filter_Top_500),
    Top1000(1000, Res.string.Market_Filter_Top_1000),
    Top1500(1500, Res.string.Market_Filter_Top_1500),
}

enum class Range(val titleResId: StringResource, val values: Pair<Long?, Long?>) {
    Range_0_5M(Res.string.Market_Filter_Range_0_5M, Pair(null, 5_000_000)),
    Range_5M_20M(Res.string.Market_Filter_Range_5M_20M, Pair(5_000_000, 20_000_000)),
    Range_20M_100M(Res.string.Market_Filter_Range_20M_100M, Pair(20_000_000, 100_000_000)),
    Range_100M_1B(Res.string.Market_Filter_Range_100M_1B, Pair(100_000_000, 1_000_000_000)),
    Range_1B_5B(Res.string.Market_Filter_Range_1B_5B, Pair(1_000_000_000, 5_000_000_000)),
    Range_5B_More(Res.string.Market_Filter_Range_5B_More, Pair(5_000_000_000, null)),

    Range_0_10M(Res.string.Market_Filter_Range_0_10M, Pair(null, 10_000_000)),
    Range_10M_40M(Res.string.Market_Filter_Range_10M_40M, Pair(10_000_000, 40_000_000)),
    Range_40M_200M(Res.string.Market_Filter_Range_40M_200M, Pair(40_000_000, 200_000_000)),
    Range_200M_2B(Res.string.Market_Filter_Range_200M_2B, Pair(200_000_000, 2_000_000_000)),
    Range_2B_10B(Res.string.Market_Filter_Range_2B_10B, Pair(2_000_000_000, 10_000_000_000)),
    Range_10B_More(Res.string.Market_Filter_Range_10B_More, Pair(10_000_000_000, null)),

    Range_0_50M(Res.string.Market_Filter_Range_0_50M, Pair(null, 50_000_000)),
    Range_50M_200M(Res.string.Market_Filter_Range_50M_200M, Pair(50_000_000, 200_000_000)),
    Range_200M_1B(Res.string.Market_Filter_Range_200M_1B, Pair(200_000_000, 1_000_000_000)),
    Range_1B_10B(Res.string.Market_Filter_Range_1B_10B, Pair(1_000_000_000, 10_000_000_000)),
    Range_10B_50B(Res.string.Market_Filter_Range_10B_50B, Pair(10_000_000_000, 50_000_000_000)),
    Range_50B_More(Res.string.Market_Filter_Range_50B_More, Pair(50_000_000_000, null)),

    Range_0_500M(Res.string.Market_Filter_Range_0_500M, Pair(null, 500_000_000)),
    Range_500M_2B(Res.string.Market_Filter_Range_500M_2B, Pair(500_000_000, 2_000_000_000)),
    Range_10B_100B(Res.string.Market_Filter_Range_10B_100B, Pair(10_000_000_000, 100_000_000_000)),
    Range_100B_500B(Res.string.Market_Filter_Range_100B_500B, Pair(100_000_000_000, 500_000_000_000)),
    Range_500B_More(Res.string.Market_Filter_Range_500B_More, Pair(500_000_000_000, null));

    companion object {
        fun valuesByCurrency(currencyCode: String) = when (currencyCode) {
            "USD",
            "EUR",
            "GBP",
            "AUD",
            "CAD",
            "CHF",
            "SGD",
            -> listOf(
                Range_0_5M,
                Range_5M_20M,
                Range_20M_100M,
                Range_100M_1B,
                Range_1B_5B,
                Range_5B_More,
            )
            "JPY",
            -> listOf(
                Range_0_500M,
                Range_500M_2B,
                Range_2B_10B,
                Range_10B_100B,
                Range_100B_500B,
                Range_500B_More,
            )
            "BRL",
            "CNY",
            "HKD",
            -> listOf(
                Range_0_50M,
                Range_50M_200M,
                Range_200M_1B,
                Range_1B_10B,
                Range_10B_50B,
                Range_50B_More,
            )
            "ILS",
            -> listOf(
                Range_0_10M,
                Range_10M_40M,
                Range_40M_200M,
                Range_200M_2B,
                Range_2B_10B,
                Range_10B_More,
            )
            "RUB",
            -> listOf(
                Range_0_500M,
                Range_500M_2B,
                Range_2B_10B,
                Range_10B_100B,
                Range_100B_500B,
                Range_500B_More,
            )
            else -> listOf()
        }
    }
}

enum class TimePeriod(val titleResId: StringResource): WithTranslatableTitle {
    TimePeriod_1D(Res.string.Market_Filter_TimePeriod_1D),
    TimePeriod_1W(Res.string.Market_Filter_TimePeriod_1W),
    TimePeriod_2W(Res.string.Market_Filter_TimePeriod_2W),
    TimePeriod_1M(Res.string.Market_Filter_TimePeriod_1M),
    TimePeriod_3M(Res.string.Market_Filter_TimePeriod_3M),
    TimePeriod_6M(Res.string.Market_Filter_TimePeriod_6M),
    TimePeriod_1Y(Res.string.Market_Filter_TimePeriod_1Y);

    override val title: TranslatableString
        get() = TranslatableString.ResString(titleResId)
}

enum class PriceChange(
    val titleResId: StringResource,
    val color: TextColor,
    val values: Pair<Long?, Long?>
) {
    Positive_10_plus(
        Res.string.Market_Filter_PriceChange_Positive_10_plus,
        TextColor.Remus,
        Pair(10, null)
    ),
    Positive_25_plus(
        Res.string.Market_Filter_PriceChange_Positive_25_plus,
        TextColor.Remus,
        Pair(25, null)
    ),
    Positive_50_plus(
        Res.string.Market_Filter_PriceChange_Positive_50_plus,
        TextColor.Remus,
        Pair(50, null)
    ),
    Positive_100_plus(
        Res.string.Market_Filter_PriceChange_Positive_100_plus,
        TextColor.Remus,
        Pair(100, null)
    ),
    Negative_10_minus(
        Res.string.Market_Filter_PriceChange_Negative_10_minus,
        TextColor.Lucian,
        Pair(null, -10)
    ),
    Negative_25_minus(
        Res.string.Market_Filter_PriceChange_Negative_25_minus,
        TextColor.Lucian,
        Pair(null, -25)
    ),
    Negative_50_minus(
        Res.string.Market_Filter_PriceChange_Negative_50_minus,
        TextColor.Lucian,
        Pair(null, -50)
    ),
    Negative_75_minus(
        Res.string.Market_Filter_PriceChange_Negative_75_minus,
        TextColor.Lucian,
        Pair(null, -75)
    ),
}

enum class TextColor{
    Remus, Lucian, Grey, Leah
}

class FilterViewItemWrapper<T>(val title: String?, val item: T) {
    override fun equals(other: Any?) = when {
        other !is FilterViewItemWrapper<*> -> false
        else -> item == other.item
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (item?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun <T>getAny(): FilterViewItemWrapper<T?> {
            return FilterViewItemWrapper(null, null)
        }
    }
}

interface IMarketListFetcher {
    fun fetchAsync(): Single<List<MarketItem>>
}
