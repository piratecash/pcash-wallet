package cash.p.terminal.shared.desktop

import cash.p.terminal.core.managers.DefaultCurrencyManager
import cash.p.terminal.core.managers.NumberFormatter
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.modules.chart.ChartIndicatorStorage
import cash.p.terminal.modules.coin.overview.MarketFeatureConfig
import cash.p.terminal.modules.coin.overview.TokenWalletActions
import cash.p.terminal.modules.market.MarketPages
import cash.p.terminal.network.desktopAppDataFile
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.market.MarketCatalogSyncer
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import com.google.gson.Gson
import io.horizontalsystems.core.AppForegroundState
import io.horizontalsystems.core.BaseCurrencyStorage
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DefaultDispatcherProvider
import io.horizontalsystems.core.DesktopAppForegroundState
import io.horizontalsystems.core.DesktopClipboardManager
import io.horizontalsystems.core.DesktopLanguageManager
import io.horizontalsystems.core.DesktopPriceChangeIntervalProvider
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ILanguageManager
import io.horizontalsystems.core.MarketSettingsStore
import io.horizontalsystems.core.PriceChangeIntervalProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

private const val GUIDES_URL =
    "https://raw.githubusercontent.com/horizontalsystems/Unstoppable-Wallet-Website/refs/tags/v1.4/src/edu.json"

/** Desktop implementations of the seams `feature-market` resolves from the host Koin graph. */
val desktopFeatureModule = module {
    single { DesktopSettingsStore(desktopAppDataFile("settings.preferences_pb")) } binds
        arrayOf(MarketSettingsStore::class, BaseCurrencyStorage::class)
    single<ChartIndicatorStorage> {
        DesktopChartIndicatorStorage(desktopAppDataFile("chart_indicators.preferences_pb"), Gson(), get())
    }
    single { MarketFeatureConfig(GUIDES_URL) }

    singleOf(::DefaultCurrencyManager) bind CurrencyManager::class
    singleOf(::NumberFormatter) bind IAppNumberFormatter::class
    singleOf(::DesktopLanguageManager) bind ILanguageManager::class
    singleOf(::DesktopAppForegroundState) bind AppForegroundState::class
    singleOf(::DesktopPriceChangeIntervalProvider) bind PriceChangeIntervalProvider::class
    singleOf(::DesktopClipboardManager) bind IClipboardManager::class
    singleOf(::DefaultDispatcherProvider) bind DispatcherProvider::class

    singleOf(::DesktopMarketPages) bind MarketPages::class
    singleOf(::DesktopTokenWalletActions) bind TokenWalletActions::class
    singleOf(::DesktopWalletManager) bind IWalletManager::class
    singleOf(::DesktopAccountManager) bind IAccountManager::class
    singleOf(::DesktopHudMessenger) bind HudMessenger::class
    singleOf(::DesktopUrlOpener) bind UrlOpener::class
    singleOf(::MarketCatalogSyncer)
}
