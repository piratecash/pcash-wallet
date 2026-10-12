package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.IMarketStorage
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.market.MarketModule
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.modules.market.favorites.WatchlistSorting
import io.horizontalsystems.core.MarketSettingsStore
import kotlinx.coroutines.flow.StateFlow

class AndroidMarketSettingsStore(
    private val localStorage: ILocalStorage,
    private val marketStorage: IMarketStorage
) : MarketSettingsStore {

    override var marketSearchRecentCoinUids by localStorage::marketSearchRecentCoinUids
    override var marketFavoritesShowSignals by localStorage::marketFavoritesShowSignals
    override var chartIndicatorsEnabled by localStorage::chartIndicatorsEnabled
    override var marketsTabEnabled by localStorage::marketsTabEnabled
    override val marketsTabEnabledFlow: StateFlow<Boolean> get() = localStorage.marketsTabEnabledFlow

    override var marketFavoritesSorting: String?
        get() = localStorage.marketFavoritesSorting?.name
        set(value) {
            localStorage.marketFavoritesSorting = value.toEnumOrNull<WatchlistSorting>()
        }

    override var marketFavoritesPeriod: String?
        get() = localStorage.marketFavoritesPeriod?.name
        set(value) {
            localStorage.marketFavoritesPeriod = value.toEnumOrNull<TimeDuration>()
        }

    override var launchPage: String?
        get() = localStorage.launchPage?.name
        set(value) {
            localStorage.launchPage = value.toEnumOrNull<LaunchPage>()
        }

    override var currentMarketTab: String?
        get() = marketStorage.currentMarketTab?.name
        set(value) {
            marketStorage.currentMarketTab = value.toEnumOrNull<MarketModule.Tab>()
        }

    private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
        enumValues<E>().firstOrNull { it.name == this }
}
