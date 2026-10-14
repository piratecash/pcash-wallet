package cash.p.terminal.modules.market.favorites

import cash.p.terminal.resources.Market_Sorting_Gainers
import cash.p.terminal.resources.Market_Sorting_HighestCap
import cash.p.terminal.resources.Market_Sorting_Losers
import cash.p.terminal.resources.Market_Sorting_LowestCap
import cash.p.terminal.resources.Market_Sorting_Manual
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.market.MarketViewItem
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import org.jetbrains.compose.resources.StringResource

object MarketFavoritesModule {

    data class UiState(
        val viewItems: List<MarketViewItem>,
        val viewState: ViewState,
        val isRefreshing: Boolean,
        val sortingField: WatchlistSorting,
        val period: TimeDuration,
        val showSignal: Boolean,
        val showSignalsInfo: Boolean
    )

}

enum class WatchlistSorting(val titleResId: StringResource): WithTranslatableTitle {
    Manual(Res.string.Market_Sorting_Manual),
    HighestCap(Res.string.Market_Sorting_HighestCap),
    LowestCap(Res.string.Market_Sorting_LowestCap),
    Gainers(Res.string.Market_Sorting_Gainers),
    Losers(Res.string.Market_Sorting_Losers);

    override val title = TranslatableString.ResString(titleResId)
}
