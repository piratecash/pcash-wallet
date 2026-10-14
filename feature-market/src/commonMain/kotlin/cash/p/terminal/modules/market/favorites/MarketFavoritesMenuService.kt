package cash.p.terminal.modules.market.favorites

import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.wallet.favorites.MarketFavoritesChangeListener
import io.horizontalsystems.core.MarketSettingsStore

class MarketFavoritesMenuService(
    private val settings: MarketSettingsStore,
    private val changeListener: MarketFavoritesChangeListener
) {

    var listSorting: WatchlistSorting
        get() = settings.marketFavoritesSorting.toEnumOr(WatchlistSorting.Manual)
        set(value) {
            settings.marketFavoritesSorting = value.name
            changeListener.onFavoritesChanged()
        }

    var timeDuration: TimeDuration
        get() = settings.marketFavoritesPeriod.toEnumOr(TimeDuration.OneDay)
        set(value) {
            settings.marketFavoritesPeriod = value.name
            changeListener.onFavoritesChanged()
        }

    var showSignals: Boolean
        get() = settings.marketFavoritesShowSignals
        set(value) {
            settings.marketFavoritesShowSignals = value
            changeListener.onFavoritesChanged()
        }

    private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default
}
