package cash.p.terminal.widgets

import cash.p.terminal.wallet.favorites.MarketFavoritesChangeListener

class WidgetMarketFavoritesChangeListener(
    private val marketWidgetManager: MarketWidgetManager,
) : MarketFavoritesChangeListener {

    override fun onFavoritesChanged() {
        marketWidgetManager.updateWatchListWidgets()
    }
}
