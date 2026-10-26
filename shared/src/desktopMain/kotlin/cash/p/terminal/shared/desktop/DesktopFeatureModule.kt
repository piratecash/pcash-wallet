package cash.p.terminal.shared.desktop

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.core.managers.DefaultCurrencyManager
import cash.p.terminal.core.managers.NumberFormatter
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.chart.ChartIndicatorStorage
import cash.p.terminal.modules.coin.overview.MarketFeatureConfig
import cash.p.terminal.modules.market.MarketPages
import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.ILockoutUntilDateFactory
import cash.p.terminal.modules.pin.core.LockoutManager
import cash.p.terminal.modules.pin.core.LockoutUntilDateFactory
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.network.desktopAppDataDirectory
import cash.p.terminal.network.desktopAppDataFile
import cash.p.terminal.shared.desktop.lock.ChangePasswordViewModel
import cash.p.terminal.shared.desktop.lock.ConfirmPasswordViewModel
import cash.p.terminal.shared.desktop.lock.CreatePasswordViewModel
import cash.p.terminal.shared.desktop.lock.DesktopInactivityLock
import cash.p.terminal.shared.desktop.lock.ForgotPasswordViewModel
import cash.p.terminal.shared.desktop.lock.UnlockViewModel
import cash.p.terminal.shared.desktop.settings.DesktopAutoLockIntervalsViewModel
import cash.p.terminal.shared.desktop.settings.DesktopLanguageSettingsViewModel
import cash.p.terminal.shared.desktop.settings.DesktopSecurityViewModel
import cash.p.terminal.shared.desktop.settings.DesktopSettingsPages
import cash.p.terminal.shared.desktop.vault.DefaultSqlCipherDriverFactory
import cash.p.terminal.shared.desktop.vault.DesktopPreferencesReset
import cash.p.terminal.shared.desktop.vault.DesktopSecretFieldsEncryption
import cash.p.terminal.shared.desktop.vault.DesktopUptimeProvider
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.shared.desktop.vault.SqlCipherDriverFactory
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.main.MainTabStorage
import cash.p.terminal.shared.market.MarketCatalogSyncer
import cash.p.terminal.shared.settings.SettingsPages
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.feature.wallet.di.featureWalletModule
import cash.p.terminal.featureStacking.di.featureStackingModule
import cash.p.terminal.premium.di.featurePremiumModule
import cash.p.terminal.wallet.di.walletFeatureModule
import com.google.gson.Gson
import io.horizontalsystems.core.AppForegroundState
import io.horizontalsystems.core.BaseCurrencyStorage
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.CurrentDateProvider
import io.horizontalsystems.core.DefaultDispatcherProvider
import io.horizontalsystems.core.DesktopAppForegroundState
import io.horizontalsystems.core.DesktopClipboardManager
import io.horizontalsystems.core.DesktopLanguageManager
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.ICurrentDateProvider
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.core.ILanguageManager
import io.horizontalsystems.core.ILockoutStorage
import io.horizontalsystems.core.MarketSettingsStore
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

private const val GUIDES_URL =
    "https://raw.githubusercontent.com/horizontalsystems/Unstoppable-Wallet-Website/refs/tags/v1.4/src/edu.json"

/** Desktop implementations of the seams the shared features resolve from the host Koin graph. */
val desktopFeatureModule = module {
    includes(walletFeatureModule, featureWalletModule, featureStackingModule, featurePremiumModule, desktopWalletModule)

    single { DesktopSettingsStore(desktopAppDataFile("settings.preferences_pb")) } binds
        arrayOf(MarketSettingsStore::class, BaseCurrencyStorage::class, ILockoutStorage::class)
    singleOf(::DesktopAppearanceSettingsStore) binds arrayOf(AppearanceSettingsStore::class, MainTabStorage::class)
    single {
        DesktopChartIndicatorStorage(desktopAppDataFile("chart_indicators.preferences_pb"), Gson(), get())
    } bind ChartIndicatorStorage::class
    single { MarketFeatureConfig(GUIDES_URL) }

    singleOf(::DefaultCurrencyManager) bind CurrencyManager::class
    singleOf(::NumberFormatter) bind IAppNumberFormatter::class
    singleOf(::DesktopLanguageManager) bind ILanguageManager::class
    singleOf(::DesktopAppForegroundState) bind AppForegroundState::class
    singleOf(::DesktopBackgroundManager) bind IBackgroundManager::class
    single<NetworkInterfaceProbe> { SystemNetworkInterfaceProbe }
    singleOf(::DesktopConnectivityManager) bind IConnectivityManager::class
    singleOf(::DesktopClipboardManager) bind IClipboardManager::class
    singleOf(::DefaultDispatcherProvider) bind DispatcherProvider::class

    single { DesktopVaultFiles(desktopAppDataDirectory()) }
    single { PasswordVault(get<DesktopVaultFiles>().vaultFile) }
    singleOf(::DesktopUptimeProvider) bind UptimeProvider::class
    singleOf(::CurrentDateProvider) bind ICurrentDateProvider::class
    singleOf(::LockoutUntilDateFactory) bind ILockoutUntilDateFactory::class
    singleOf(::LockoutManager) bind ILockoutManager::class
    singleOf(::DefaultSqlCipherDriverFactory) bind SqlCipherDriverFactory::class
    singleOf(::DesktopPreferencesReset)
    singleOf(::DesktopVault)
    singleOf(::DesktopAppExit)
    singleOf(::ProcessAppRelauncher) bind DesktopAppRelauncher::class
    singleOf(::DesktopWipeCoordinator)
    singleOf(::DesktopSecretFieldsEncryption) bind IEncryptionManager::class
    singleOf(::DesktopInactivityLock)
    singleOf(::DesktopSettingsPages) bind SettingsPages::class

    viewModelOf(::CreatePasswordViewModel)
    viewModelOf(::UnlockViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ChangePasswordViewModel)
    viewModelOf(::ConfirmPasswordViewModel)
    viewModelOf(::DesktopSecurityViewModel)
    viewModelOf(::DesktopAutoLockIntervalsViewModel)
    viewModelOf(::DesktopLanguageSettingsViewModel)

    singleOf(::DesktopMarketPages) bind MarketPages::class
    singleOf(::DesktopHudMessenger) bind HudMessenger::class
    singleOf(::DesktopUrlOpener) bind UrlOpener::class
    singleOf(::MarketCatalogSyncer)
}
