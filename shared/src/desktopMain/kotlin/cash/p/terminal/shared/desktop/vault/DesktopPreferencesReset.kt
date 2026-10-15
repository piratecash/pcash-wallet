package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.managers.DefaultCurrencyManager
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DesktopLanguageManager
import io.horizontalsystems.core.DesktopSettingsStore

/** The stores that live outside the encrypted database and a wipe has to put back to defaults. */
class DesktopPreferencesReset(
    private val settingsStore: DesktopSettingsStore,
    private val favoritesManager: MarketFavoritesManager,
    private val currencyManager: CurrencyManager,
    private val chartIndicatorManager: ChartIndicatorManager,
    private val languageManager: DesktopLanguageManager,
) {
    suspend fun resetToDefaults() {
        settingsStore.clearAll()
        favoritesManager.clear()
        currencyManager.setBaseCurrencyCode(DefaultCurrencyManager.DEFAULT_CURRENCY_CODE)
        chartIndicatorManager.resetToDefaults()
        languageManager.reset()
    }
}
