package cash.p.terminal.wallet.favorites

/** Notified after every favorites mutation so platform UI (Android widgets) can refresh. */
interface MarketFavoritesChangeListener {
    fun onFavoritesChanged()
}
