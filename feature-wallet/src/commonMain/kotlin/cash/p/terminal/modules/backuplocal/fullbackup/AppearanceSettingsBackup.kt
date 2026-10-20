package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.settings.appearance.LaunchScreenService
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

/** The appearance part of the settings backup, the same on every platform. */
class AppearanceSettingsBackup(
    private val themeService: ThemeService,
    private val balanceViewTypeManager: BalanceViewTypeManager,
    private val launchScreenService: LaunchScreenService,
    private val store: AppearanceSettingsStore,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logger = Logger.withTag("AppearanceSettingsBackup")

    val theme: ThemeType get() = themeService.selectedTheme
    val balanceViewType: BalanceViewType get() = balanceViewTypeManager.balanceViewTypeFlow.value
    val launchScreen: LaunchPage get() = launchScreenService.selectedLaunchScreen
    val marketsTabEnabled: Boolean get() = store.marketsTabEnabled
    val balanceHideButtons: Boolean get() = store.balanceTabButtonsEnabled
    val priceChangeMode: PriceChangeInterval get() = store.priceChangeInterval
    val balanceAutoHidden: Boolean get() = balanceHiddenManager.balanceAutoHidden

    suspend fun restore(settings: Settings) {
        settings.balanceViewType?.let(balanceViewTypeManager::setViewType)
        settings.currentTheme?.let { restoreTheme(it) }
        settings.launchScreen?.let(launchScreenService::setLaunchScreen)
        settings.marketsTabEnabled?.let { store.marketsTabEnabled = it }
        settings.balanceHideButtons?.let { store.balanceTabButtonsEnabled = it }
        settings.priceChangeMode?.let { store.priceChangeInterval = it }
        settings.balanceAutoHidden?.let(balanceHiddenManager::setBalanceAutoHidden)
    }

    // Android applies the night mode through AppCompatDelegate, which must run on main.
    private suspend fun restoreTheme(theme: ThemeType) = withContext(dispatcherProvider.main) {
        try {
            themeService.setThemeType(theme)
        } catch (e: Exception) {
            logger.e(e) { "Failed to restore theme" }
        }
    }
}
