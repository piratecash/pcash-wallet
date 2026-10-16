package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.managers.DefaultCurrencyManager
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.settings.appearance.LaunchScreenService
import cash.p.terminal.modules.theme.ThemeService
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
    private val themeService: ThemeService,
    private val launchScreenService: LaunchScreenService,
    private val balanceViewTypeManager: BalanceViewTypeManager,
) {
    /** Also drops the stored language; the screen switches only in [applySystemLanguage]. */
    suspend fun resetStored() {
        settingsStore.clearAll()
        favoritesManager.clear()
        currencyManager.setBaseCurrencyCode(DefaultCurrencyManager.DEFAULT_CURRENCY_CODE)
        chartIndicatorManager.resetToDefaults()
        themeService.setThemeType(ThemeService.DEFAULT_THEME)
        launchScreenService.setLaunchScreen(LaunchScreenService.DEFAULT_LAUNCH_PAGE)
        balanceViewTypeManager.setViewType(BalanceViewTypeManager.DEFAULT_VIEW_TYPE)
    }

    /** Recreates the screens, so it runs only once nothing can fail on the current one. */
    fun applySystemLanguage() = languageManager.reset()
}
