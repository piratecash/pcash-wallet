package cash.p.terminal.modules.market

import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.resources.Market_Field_HighestCap
import cash.p.terminal.resources.Market_Field_HighestVolume
import cash.p.terminal.resources.Market_Field_LowestCap
import cash.p.terminal.resources.Market_Field_LowestVolume
import cash.p.terminal.resources.Market_Field_MarketCap
import cash.p.terminal.resources.Market_Field_PriceDiff
import cash.p.terminal.resources.Market_Field_Volume
import cash.p.terminal.resources.Market_Filter_TimePeriod_1D
import cash.p.terminal.resources.Market_Filter_TimePeriod_1M
import cash.p.terminal.resources.Market_Filter_TimePeriod_1W
import cash.p.terminal.resources.Market_Tab_Coins
import cash.p.terminal.resources.Market_Tab_Pairs
import cash.p.terminal.resources.Market_Tab_Platform
import cash.p.terminal.resources.Market_Tab_Posts
import cash.p.terminal.resources.Market_Tab_Watchlist
import cash.p.terminal.resources.Market_Top_100
import cash.p.terminal.resources.Market_Top_200
import cash.p.terminal.resources.Market_Top_300
import cash.p.terminal.resources.Market_Top_500
import cash.p.terminal.resources.RateList_TopGainers
import cash.p.terminal.resources.RateList_TopLosers
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.modules.market.filters.TimePeriod
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import cash.p.terminal.ui_compose.components.ImageSource
import io.horizontalsystems.core.entities.Value
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.models.MarketGlobal
import cash.p.terminal.wallet.models.MarketInfo
import io.horizontalsystems.core.PriceChangeIntervalProvider
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import java.math.BigDecimal
import org.jetbrains.compose.resources.StringResource

object MarketModule {

    data class UiState(
        val selectedTab: Tab,
        val marketGlobal: MarketGlobal?,
        val currency: Currency
    )

    enum class Tab(val titleResId: StringResource) {
        Coins(Res.string.Market_Tab_Coins),
        Watchlist(Res.string.Market_Tab_Watchlist),
        Posts(Res.string.Market_Tab_Posts),
        Platform(Res.string.Market_Tab_Platform),
        Pairs(Res.string.Market_Tab_Pairs);
//        Sectors(Res.string.Market_Tab_Sectors);

        companion object {
            private val map = entries.associateBy(Tab::name)

            fun fromString(type: String?): Tab? = map[type]
        }
    }

    enum class ListType(val sortingField: SortingField, val marketField: MarketField) {
        TopGainers(SortingField.TopGainers, MarketField.PriceDiff),
        TopLosers(SortingField.TopLosers, MarketField.PriceDiff),
    }

    data class Header(
        val title: String,
        val description: String,
        val icon: ImageSource,
    )
}

data class MarketItem(
    val fullCoin: FullCoin,
    val volume: CurrencyValue,
    val rate: CurrencyValue,
    val diff: BigDecimal?,
    val marketCap: CurrencyValue,
    val rank: Int?
) {
    companion object {
        fun createFromCoinMarket(
            marketInfo: MarketInfo,
            currency: Currency,
            period: TimePeriod = TimePeriod.TimePeriod_1D
        ): MarketItem {
            return MarketItem(
                fullCoin = marketInfo.fullCoin,
                volume = CurrencyValue(currency, marketInfo.totalVolume ?: BigDecimal.ZERO),
                rate = CurrencyValue(currency, marketInfo.price ?: BigDecimal.ZERO),
                diff = marketInfo.priceChangeValue(period),
                marketCap = CurrencyValue(currency, marketInfo.marketCap ?: BigDecimal.ZERO),
                rank = marketInfo.marketCapRank
            )
        }
    }
}

fun List<MarketItem>.sort(sortingField: SortingField) = when (sortingField) {
    SortingField.HighestCap -> sortedByDescendingNullLast { it.marketCap.value }
    SortingField.LowestCap -> sortedByNullLast { it.marketCap.value }
    SortingField.HighestVolume -> sortedByDescendingNullLast { it.volume.value }
    SortingField.LowestVolume -> sortedByNullLast { it.volume.value }
    SortingField.TopGainers -> sortedByDescendingNullLast { it.diff }
    SortingField.TopLosers -> sortedByNullLast { it.diff }
}

@CommonParcelize
enum class SortingField : WithTranslatableTitle, CommonParcelable {
    HighestCap, LowestCap, HighestVolume, LowestVolume, TopGainers, TopLosers;

    val titleResId: StringResource
        get() = when (this) {
            HighestCap -> Res.string.Market_Field_HighestCap
            LowestCap -> Res.string.Market_Field_LowestCap
            HighestVolume -> Res.string.Market_Field_HighestVolume
            LowestVolume -> Res.string.Market_Field_LowestVolume
            TopGainers -> Res.string.RateList_TopGainers
            TopLosers -> Res.string.RateList_TopLosers
        }

    override val title: TranslatableString
        get() = TranslatableString.ResString(titleResId)

    companion object {
        val map = values().associateBy(SortingField::name)
        fun fromString(type: String?): SortingField? = map[type]
    }
}

@CommonParcelize
enum class MarketField : WithTranslatableTitle, CommonParcelable {
    PriceDiff, MarketCap, Volume;

    val titleResId: StringResource
        get() = when (this) {
            PriceDiff -> Res.string.Market_Field_PriceDiff
            MarketCap -> Res.string.Market_Field_MarketCap
            Volume -> Res.string.Market_Field_Volume
        }

    fun next() = values()[if (ordinal == values().size - 1) 0 else ordinal + 1]

    override val title: TranslatableString
        get() = TranslatableString.ResString(titleResId)

    companion object {
        val map = values().associateBy(MarketField::name)
        fun fromString(type: String?): MarketField? = map[type]
    }
}

@CommonParcelize
enum class TopMarket(val value: Int) : WithTranslatableTitle, CommonParcelable {
    Top100(100), Top200(200), Top300(300), Top500(500);

    val titleResId: StringResource
        get() = when (this) {
            Top100 -> Res.string.Market_Top_100
            Top200 -> Res.string.Market_Top_200
            Top300 -> Res.string.Market_Top_300
            Top500 -> Res.string.Market_Top_500
        }

    fun next() = entries[if (ordinal == entries.size - 1) 0 else ordinal + 1]

    override val title: TranslatableString
        get() = TranslatableString.ResString(titleResId)
}

sealed class MarketDataValue {
    class MarketCap(val value: String) : MarketDataValue()
    class Volume(val value: String) : MarketDataValue()
    class Diff(val value: BigDecimal?) : MarketDataValue()
    class DiffNew(val value: Value) : MarketDataValue()
}

inline fun <T, R : Comparable<R>> Iterable<T>.sortedByDescendingNullLast(crossinline selector: (T) -> R?): List<T> {
    return sortedWith(compareBy(nullsFirst(), selector)).sortedByDescending(selector)
}

inline fun <T, R : Comparable<R>> Iterable<T>.sortedByNullLast(crossinline selector: (T) -> R?): List<T> {
    return sortedWith(compareBy(nullsLast(), selector))
}

fun MarketInfo.priceChangeValue(period: TimePeriod) = when (period) {
    TimePeriod.TimePeriod_1D -> {
        when (getKoinInstance<PriceChangeIntervalProvider>().interval) {
            PriceChangeInterval.LAST_24H -> priceChange24h
            PriceChangeInterval.FROM_UTC_MIDNIGHT -> priceChange1d
        }
    }
    TimePeriod.TimePeriod_1W -> priceChange7d
    TimePeriod.TimePeriod_2W -> priceChange14d
    TimePeriod.TimePeriod_1M -> priceChange30d
    TimePeriod.TimePeriod_3M -> priceChange90d
    TimePeriod.TimePeriod_6M -> priceChange200d
    TimePeriod.TimePeriod_1Y -> priceChange1y
}

@CommonParcelize
enum class TimeDuration : WithTranslatableTitle, CommonParcelable {
    OneDay, SevenDay, ThirtyDay;

    val titleResId: StringResource
        get() = when (this) {
            OneDay -> Res.string.Market_Filter_TimePeriod_1D
            SevenDay -> Res.string.Market_Filter_TimePeriod_1W
            ThirtyDay -> Res.string.Market_Filter_TimePeriod_1M
        }

    override val title: TranslatableString
        get() = TranslatableString.ResString(titleResId)
}
