package io.horizontalsystems.core

import kotlinx.coroutines.flow.StateFlow

/** Market screen preferences as strings and primitives; enum conversion is up to the market code. */
interface MarketSettingsStore {
    var marketSearchRecentCoinUids: List<String>
    var marketFavoritesSorting: String?
    var marketFavoritesPeriod: String?
    var marketFavoritesShowSignals: Boolean
    var chartIndicatorsEnabled: Boolean
    var marketsTabEnabled: Boolean
    val marketsTabEnabledFlow: StateFlow<Boolean>
    var launchPage: String?
    var currentMarketTab: String?
}
