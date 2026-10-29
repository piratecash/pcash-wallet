package cash.p.terminal.di

import cash.p.terminal.ui.compose.BalanceHideOnFlipHandler
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.NetworkSettingsStorage
import io.horizontalsystems.core.IBackgroundManager
import cash.p.terminal.core.managers.AndroidEvmHardwareSigners
import cash.p.terminal.core.managers.AndroidPlatformAccountDataCleaner
import cash.p.terminal.core.managers.PlatformAccountDataCleaner
import cash.p.terminal.core.adapters.zcash.AndroidZcashHardwareSigners
import cash.p.terminal.core.adapters.zcash.ZcashHardwareSigners
import cash.p.terminal.core.adapters.AndroidBitcoinHardwareSigners
import cash.p.terminal.core.adapters.BitcoinHardwareSigners
import cash.p.terminal.core.managers.AndroidKitPlatformContexts
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.core.managers.EvmHardwareSigners
import cash.p.terminal.core.managers.AndroidSolanaHardwareSigners
import cash.p.terminal.core.managers.AndroidStellarHardwareSigners
import cash.p.terminal.core.managers.AndroidTonHardwareSigners
import cash.p.terminal.core.managers.AndroidTronHardwareSigners
import cash.p.terminal.core.managers.SolanaHardwareSigners
import cash.p.terminal.core.managers.StellarHardwareSigners
import cash.p.terminal.core.managers.TonHardwareSigners
import cash.p.terminal.core.managers.TronHardwareSigners
import cash.p.terminal.core.managers.KitPlatformContexts
import cash.p.terminal.wallet.policy.PlatformChainSupport
import cash.p.terminal.wallet.policy.AndroidPlatformChainSupport
import cash.p.terminal.wallet.policy.AndroidPlatformOperationSupport
import cash.p.terminal.entities.UnstoppableProviderTitles
import cash.p.terminal.modules.premium.settings.AndroidPremiumPlatformSettings
import cash.p.terminal.modules.premium.settings.PremiumPlatformSettings
import cash.p.terminal.modules.multiswap.providers.AndroidUnstoppableProviderTitles
import cash.p.terminal.wallet.policy.PlatformOperationSupport
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.preference.PreferenceManager
import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.core.CrashlyticsNonFatalReporter
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.IMarketStorage
import cash.p.terminal.core.IRateAppManager
import cash.p.terminal.core.NonFatalReporter
import cash.p.terminal.core.ITorManager
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKeyProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKeyProviderImpl
import cash.p.terminal.core.deeplink.DeeplinkParser
import cash.p.terminal.core.managers.AppHeadersProviderImpl
import cash.p.terminal.core.managers.BalanceHideOnFlipManager
import cash.p.terminal.core.managers.BeamDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamKitDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamResetMarker
import cash.p.terminal.core.managers.SharedPreferencesBeamResetMarker
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.wallet.MarketApiConfig
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import cash.p.terminal.core.managers.BitcoinKitDatabaseKeyProvider
import cash.p.terminal.core.managers.AndroidBitcoinKitDatabaseKeyProvider
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.DeviceFlipDetector
import cash.p.terminal.core.managers.EffectiveMonitoredChains
import cash.p.terminal.modules.send.offline.OfflineBroadcastTokenResolver
import android.content.Context
import android.hardware.usb.UsbManager
import io.horizontalsystems.core.common.usb.AndroidUsbPermissionRequester
import android.net.ConnectivityManager as AndroidConnectivityManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.notifications.NotificationDeduplicator
import cash.p.terminal.core.notifications.TransactionMonitor
import cash.p.terminal.core.notifications.TransactionNotificationCoordinator
import cash.p.terminal.core.notifications.TransactionNotificationManager
import cash.p.terminal.core.notifications.polling.BeamTransactionsPoller
import cash.p.terminal.core.notifications.polling.BtcLikeTransactionsPoller
import cash.p.terminal.core.notifications.polling.EvmTransactionsPoller
import cash.p.terminal.core.notifications.polling.MoneroTransactionsPoller
import cash.p.terminal.core.notifications.polling.SolanaTransactionsPoller
import cash.p.terminal.core.notifications.polling.StellarTransactionsPoller
import cash.p.terminal.core.notifications.polling.ThorchainTransactionsPoller
import cash.p.terminal.core.notifications.polling.TonTransactionsPoller
import cash.p.terminal.core.notifications.polling.TransactionPollingManager
import cash.p.terminal.core.notifications.polling.TronTransactionsPoller
import cash.p.terminal.core.notifications.polling.ZcashTransactionsPoller
import cash.p.terminal.core.managers.CreateRequiredTokensUseCaseImpl
import cash.p.terminal.core.managers.AndroidAppForegroundState
import cash.p.terminal.core.managers.AndroidMarketSettingsStore
import cash.p.terminal.core.managers.DefaultCurrencyManager
import cash.p.terminal.core.managers.EvmKitDatabaseKeyProvider
import cash.p.terminal.core.managers.AndroidEvmKitDatabaseKeyProvider
import cash.p.terminal.core.managers.MarketFavoritesDataMigration
import cash.p.terminal.core.managers.DefaultMoneroDeviceWalletNative
import cash.p.terminal.core.managers.DefaultMoneroNativeWalletRuntime
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.EvmPersonalSignerImpl
import cash.p.terminal.core.managers.GetTonAddressUseCaseImpl
import cash.p.terminal.core.managers.GuidesManager
import cash.p.terminal.core.managers.KeyStoreCleaner
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.strings.helpers.AndroidLocalizedResources
import cash.p.terminal.core.managers.LocalStorageManager
import cash.p.terminal.modules.blockchainstatus.AndroidStatusReportBuildInfo
import cash.p.terminal.modules.blockchainstatus.AndroidStatusReportExporter
import cash.p.terminal.modules.blockchainstatus.StatusReportBuildInfo
import cash.p.terminal.modules.blockchainstatus.StatusReportExporter
import cash.p.terminal.modules.blockchainstatus.StatusReportFile
import cash.p.terminal.core.managers.MoneroNativeWalletRuntime
import cash.p.terminal.core.managers.MoneroDeviceWalletNative
import cash.p.terminal.core.managers.MoneroDeviceWalletFileStore
import cash.p.terminal.core.managers.MoneroDeviceWalletProvisioner
import cash.p.terminal.core.managers.MoneroTrezorReadiness
import cash.p.terminal.core.managers.MoneroDeviceOperationGateway
import cash.p.terminal.core.managers.MoneroLedgerOperationGateway
import cash.p.terminal.core.managers.MoneroTrezorOperationGateway
import cash.p.terminal.core.managers.MoneroHardwareGateway
import cash.p.terminal.core.managers.RateAppManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.managers.ReleaseNotesManager
import cash.p.terminal.core.managers.SilentCameraManager
import cash.p.terminal.core.managers.SolanaKitDatabaseKeyProvider
import cash.p.terminal.core.managers.AndroidSolanaKitDatabaseKeyProvider
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.managers.StellarKitDatabaseKeyProvider
import cash.p.terminal.core.managers.AndroidStellarKitDatabaseKeyProvider
import cash.p.terminal.core.managers.SystemInfoManager
import cash.p.terminal.core.managers.TonConnectDatabaseKeyProvider
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.core.managers.TonKitDatabaseKeyProvider
import cash.p.terminal.core.managers.AndroidTonKitDatabaseKeyProvider
import cash.p.terminal.core.managers.TorManager
import cash.p.terminal.core.managers.TronKitDatabaseKeyProvider
import cash.p.terminal.core.managers.AndroidTronKitDatabaseKeyProvider
import cash.p.terminal.core.INetworkManager
import cash.p.terminal.core.managers.NetworkManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.softwareupdate.AppUpdateChecker
import cash.p.terminal.modules.softwareupdate.domain.CheckAppUpdateUseCase
import cash.p.terminal.modules.softwareupdate.domain.GetReleaseChangelogUseCase
import cash.p.terminal.modules.softwareupdate.domain.GetVersionHistoryUseCase
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProvider
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProviderImpl
import cash.p.terminal.modules.softwareupdate.domain.ShouldAutoCheckUseCase
import cash.p.terminal.modules.softwareupdate.domain.SystemTimeProvider
import cash.p.terminal.modules.softwareupdate.domain.TimeProvider
import cash.p.terminal.network.github.data.GithubApiConfig
import cash.p.terminal.core.providers.CompositeFallbackAddressProvider
import cash.p.terminal.core.providers.TonFallbackAddressProvider
import cash.p.terminal.core.providers.ZcashFallbackAddressProvider
import cash.p.terminal.wallet.FallbackAddressProvider
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.feature.miniapp.domain.usecase.CreateRequiredTokensUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.EvmPersonalSigner
import cash.p.terminal.feature.miniapp.domain.usecase.GetTonAddressUseCase
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.modules.backuplocal.fullbackup.AndroidAppSettingsBackup
import cash.p.terminal.modules.contacts.AndroidContactsFileStorage
import cash.p.terminal.modules.contacts.ContactsFileStorage
import cash.p.terminal.modules.backuplocal.fullbackup.BackupProvider
import cash.p.terminal.modules.backuplocal.fullbackup.ChartIndicatorsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupProvider
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.main.AppPagesImpl
import cash.p.terminal.core.ActivityAppRestarter
import cash.p.terminal.core.AndroidBackupFileLauncher
import cash.p.terminal.modules.contacts.AndroidDeleteContactsPinSettings
import cash.p.terminal.modules.contacts.DeleteContactsPinSettings
import cash.p.terminal.core.PinSecretActionAuthorizer
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.feature.wallet.PinStateInfo
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.feature.wallet.WalletSendPages
import cash.p.terminal.modules.market.MarketPages
import cash.p.terminal.modules.multiswap.AssetFiatRateService
import cash.p.terminal.modules.pin.PinComponent
import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.ILockoutUntilDateFactory
import cash.p.terminal.modules.pin.core.LockoutManager
import cash.p.terminal.modules.pin.core.AndroidUptimeProvider
import cash.p.terminal.modules.pin.core.LockoutUntilDateFactory
import cash.p.terminal.modules.pin.core.OneTimeTimer
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.modules.calculator.domain.CalculatorExpressionEvaluator
import cash.p.terminal.modules.calculator.domain.CalculatorModeService
import cash.p.terminal.modules.calculator.domain.CalculatorPinAttemptThrottle
import cash.p.terminal.modules.pin.unlock.AttemptPinUnlockUseCase
import cash.p.terminal.modules.send.offline.OfflineQrCodeSaver
import cash.p.terminal.modules.send.offline.OfflineTransactionFileTransfer
import cash.p.terminal.modules.send.offline.BeamOfflineTransactionRelay
import cash.p.terminal.modules.settings.appearance.AppIconService
import cash.p.terminal.modules.settings.appearance.LaunchScreenService
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.modules.settings.guides.GuidesRepository
import cash.p.terminal.modules.pin.hiddenwallet.HiddenWalletPinPolicy
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.WCWalletRequestHandler
import cash.p.terminal.wallet.favorites.MarketFavoritesChangeListener
import cash.p.terminal.wallet.favorites.MarketFavoritesMigration
import cash.p.terminal.widgets.MarketWatchlistResetCleaner
import cash.p.terminal.widgets.MarketWidgetManager
import cash.p.terminal.widgets.MarketWidgetRepository
import cash.p.terminal.widgets.WidgetMarketFavoritesChangeListener
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.ChartIndicatorStorageImpl
import cash.p.terminal.modules.chart.ChartIndicatorStorage
import cash.p.terminal.modules.balance.AndroidBalancePlatformActions
import cash.p.terminal.modules.balance.BalancePlatformActions
import cash.p.terminal.modules.managewallets.AndroidHardwareWalletKeyProvisioner
import cash.p.terminal.modules.managewallets.HardwareWalletKeyProvisioner
import cash.p.terminal.modules.coin.overview.MarketFeatureConfig
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.modules.walletconnect.handler.WCHandlerEvm
import cash.p.terminal.modules.walletconnect.stellar.WCHandlerStellar
import cash.p.terminal.modules.walletconnect.storage.WCPairingMetadataStorage
import cash.p.terminal.modules.walletconnect.storage.WCSessionStorage
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.pirate.di.PREMIUM_API_BASE_URL_QUALIFIER
import cash.p.terminal.premium.di.PREMIUM_IS_DEBUG_QUALIFIER
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.wallet.navigation.WalletPages
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.piratecash.monero.MoneroWalletFiles
import io.horizontalsystems.core.AppForegroundState
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BaseCurrencyStorage
import io.horizontalsystems.core.MarketSettingsStore
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.CurrentDateProvider
import io.horizontalsystems.core.ICurrentDateProvider
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.core.IKeyProvider
import io.horizontalsystems.core.IKeyStoreCleaner
import io.horizontalsystems.core.IKeyStoreManager
import io.horizontalsystems.core.ILanguageManager
import io.horizontalsystems.core.LocalizedResources
import io.horizontalsystems.core.ILockoutStorage
import io.horizontalsystems.core.ILoggingSettings
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.IPinSettingsStorage
import io.horizontalsystems.core.ISilentPhotoCapture
import io.horizontalsystems.core.ISmsNotificationSettings
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.core.security.EncryptionManager
import io.horizontalsystems.core.security.KeyStoreManager
import io.horizontalsystems.hdwalletkit.Mnemonic
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

private const val KEY_STORE_LOG_SCOPE = "key-store"

val managerModule = module {
    singleOf(::NetworkManager) bind INetworkManager::class
    singleOf(::StatusReportFile)
    singleOf(::AndroidStatusReportExporter) bind StatusReportExporter::class
    singleOf(::AndroidStatusReportBuildInfo) bind StatusReportBuildInfo::class
    singleOf(::SystemInfoManager) bind ISystemInfoManager::class
    singleOf(::LanguageManager) bind ILanguageManager::class
    singleOf(::AndroidLocalizedResources) bind LocalizedResources::class
    singleOf(::AppHeadersProviderImpl) bind AppHeadersProvider::class
    singleOf(::DefaultCurrencyManager) bind CurrencyManager::class
    singleOf(::SolanaRpcSourceManager)
    single<AddressResolutionConfig> { AppConfigProvider }
    singleOf(::CrashlyticsNonFatalReporter) bind NonFatalReporter::class
    singleOf(::ZcashDbKeyProviderImpl) bind ZcashDbKeyProvider::class
    singleOf(::TonFallbackAddressProvider)
    single<FallbackAddressProvider> {
        CompositeFallbackAddressProvider(
            providers = listOf(
                get<TonFallbackAddressProvider>(),
                get<ZcashFallbackAddressProvider>()
            )
        )
    }
    singleOf(::EffectiveMonitoredChains)
    singleOf(::LocalStorageManager) {
        bind<ILocalStorage>()
        bind<ILoggingSettings>()
        bind<ISmsNotificationSettings>()
        bind<IPinSettingsStorage>()
        bind<ILockoutStorage>()
        bind<IThirdKeyboard>()
        bind<IMarketStorage>()
        bind<BaseCurrencyStorage>()
        bind<IUniqueCodeStorage>()
        bind<WalletOnboardingStorage>()
        bind<AppearanceSettingsStore>()
        bind<BalanceSettingsStorage>()
        bind<TransactionSettingsStorage>()
        bind<NetworkSettingsStorage>()
    }
    single { PreferenceManager.getDefaultSharedPreferences(get()) }
    singleOf(::BackgroundManager) bind IBackgroundManager::class
    singleOf(::AndroidKitPlatformContexts) bind KitPlatformContexts::class
    singleOf(::AndroidEvmHardwareSigners) bind EvmHardwareSigners::class
    singleOf(::AndroidBitcoinHardwareSigners) bind BitcoinHardwareSigners::class
    singleOf(::AndroidSolanaHardwareSigners) bind SolanaHardwareSigners::class
    singleOf(::AndroidTronHardwareSigners) bind TronHardwareSigners::class
    singleOf(::AndroidStellarHardwareSigners) bind StellarHardwareSigners::class
    singleOf(::AndroidTonHardwareSigners) bind TonHardwareSigners::class
    singleOf(::AndroidZcashHardwareSigners) bind ZcashHardwareSigners::class
    singleOf(::AndroidPlatformAccountDataCleaner) bind PlatformAccountDataCleaner::class
    single<EvmBlockchains> { get<EvmBlockchainManager>() }
    single<PlatformChainSupport> { AndroidPlatformChainSupport }
    single<PlatformOperationSupport> { AndroidPlatformOperationSupport }
    singleOf(::AndroidUnstoppableProviderTitles) bind UnstoppableProviderTitles::class
    singleOf(::AndroidPremiumPlatformSettings) bind PremiumPlatformSettings::class
    singleOf(::AndroidAppForegroundState) bind AppForegroundState::class
    singleOf(::AndroidMarketSettingsStore) bind MarketSettingsStore::class
    single { TextHelper } bind IClipboardManager::class
    singleOf(::TransactionNotificationManager)
    single {
        val prefs = get<Context>()
            .getSharedPreferences("notification_dedup", Context.MODE_PRIVATE)
        NotificationDeduplicator(prefs)
    }
    singleOf(::EvmTransactionsPoller)
    singleOf(::TonTransactionsPoller)
    singleOf(::TronTransactionsPoller)
    singleOf(::SolanaTransactionsPoller)
    singleOf(::StellarTransactionsPoller)
    singleOf(::ThorchainTransactionsPoller)
    singleOf(::BtcLikeTransactionsPoller)
    singleOf(::ZcashTransactionsPoller)
    singleOf(::BeamTransactionsPoller)
    singleOf(::MoneroTransactionsPoller)
    single {
        TransactionPollingManager(
            listOf(
                get<EvmTransactionsPoller>(),
                get<TonTransactionsPoller>(),
                get<TronTransactionsPoller>(),
                get<SolanaTransactionsPoller>(),
                get<StellarTransactionsPoller>(),
                get<ThorchainTransactionsPoller>(),
                get<BtcLikeTransactionsPoller>(),
                get<ZcashTransactionsPoller>(),
                get<MoneroTransactionsPoller>(),
                get<BeamTransactionsPoller>(),
            ),
            get()
        )
    }
    singleOf(::TransactionMonitor)
    singleOf(::TransactionNotificationCoordinator)
    single {
        get<Context>().getSystemService(Context.CONNECTIVITY_SERVICE) as AndroidConnectivityManager
    }
    singleOf(::ConnectivityManager) bind IConnectivityManager::class
    singleOf(::AndroidBitcoinKitDatabaseKeyProvider) bind BitcoinKitDatabaseKeyProvider::class
    singleOf(::EvmSyncSourceManager)
    singleOf(::AndroidEvmKitDatabaseKeyProvider) bind EvmKitDatabaseKeyProvider::class
    singleOf(::BtcBlockchainManager)
    singleOf(::OfflineBroadcastTokenResolver)
    singleOf(::AndroidSolanaKitDatabaseKeyProvider) bind SolanaKitDatabaseKeyProvider::class
    singleOf(::AndroidStellarKitDatabaseKeyProvider) bind StellarKitDatabaseKeyProvider::class
    singleOf(::AndroidTonKitDatabaseKeyProvider) bind TonKitDatabaseKeyProvider::class
    singleOf(::TonConnectDatabaseKeyProvider)
    single { MarketApiConfig(AppConfigProvider.marketApiBaseUrl, MarketApiConfig.API_KEY) }
    factoryOf(::RemoveMoneroWalletFilesUseCase) bind IRemoveMoneroWalletFilesUseCase::class
    singleOf(::GetTonAddressUseCaseImpl) bind GetTonAddressUseCase::class
    singleOf(::CreateRequiredTokensUseCaseImpl) bind CreateRequiredTokensUseCase::class
    singleOf(::EvmPersonalSignerImpl) bind EvmPersonalSigner::class
    singleOf(::AndroidTronKitDatabaseKeyProvider) bind TronKitDatabaseKeyProvider::class
    singleOf(::RecentAddressManager)
    single {
        PinComponent(
            pinSettingsStorage = get(),
            userManager = get(),
            pinDbStorage = get(),
            backgroundManager = get(),
            resetUseCase = get(),
            deleteAllContactsUseCase = get(),
            dispatcherProvider = get()
        )
    } binds arrayOf(IPinComponent::class, PinStateInfo::class)
    singleOf(::PinSecretActionAuthorizer) bind SecretActionAuthorizer::class
    singleOf(::ActivityAppRestarter) bind AppRestarter::class
    singleOf(::AndroidBackupFileLauncher) bind BackupFileLauncher::class
    singleOf(::AndroidDeleteContactsPinSettings) bind DeleteContactsPinSettings::class
    singleOf(::RateAppManager) bind IRateAppManager::class
    singleOf(::ReleaseNotesManager)
    singleOf(::WCSessionStorage)
    singleOf(::WCPairingMetadataStorage)
    single {
        WCManager(accountManager = get()).also {
            it.addWcHandler(WCHandlerEvm(get(), get()))
            it.addWcHandler(WCHandlerStellar(get()))
        }
    }
    singleOf(::WCSessionManager)
    factoryOf(::WCWalletRequestHandler)

    singleOf(::Mnemonic)

    singleOf(::DefaultMoneroNativeWalletRuntime) bind MoneroNativeWalletRuntime::class
    singleOf(::MoneroTrezorOperationGateway)
    singleOf(::MoneroLedgerOperationGateway)
    singleOf(::MoneroDeviceOperationGateway) bind MoneroHardwareGateway::class

    singleOf(::MoneroTrezorReadiness)
    singleOf(::DefaultMoneroDeviceWalletNative) bind MoneroDeviceWalletNative::class
    single { MoneroDeviceWalletFileStore.create(get()) }
    singleOf(::MoneroDeviceWalletProvisioner)
    single { get<Context>().getSystemService(Context.USB_SERVICE) as UsbManager }
    singleOf(::AndroidUsbPermissionRequester)
    single { MoneroWalletService(MoneroWalletFiles.root(get())) }
    singleOf(::SilentCameraManager) bind ISilentPhotoCapture::class
    singleOf(::CurrentDateProvider) bind ICurrentDateProvider::class
    singleOf(::AndroidUptimeProvider) bind UptimeProvider::class
    singleOf(::LockoutUntilDateFactory) bind ILockoutUntilDateFactory::class
    singleOf(::LockoutManager) bind ILockoutManager::class
    factoryOf(::OneTimeTimer)
    singleOf(::GlanceAppWidgetManager)
    singleOf(::MarketWatchlistResetCleaner)
    singleOf(::AppIconService)
    factoryOf(::ThemeService)
    factoryOf(::LaunchScreenService)
    factoryOf(::ChartIndicatorsBackup)
    factoryOf(::AndroidAppSettingsBackup)
    singleOf(::AndroidContactsFileStorage) bind ContactsFileStorage::class
    factoryOf(::BackupProvider) bind WalletBackupProvider::class
    singleOf(::CalculatorModeService)
    single { CalculatorPinAttemptThrottle(get(), get()) }
    single { CalculatorExpressionEvaluator() }
    singleOf(::AttemptPinUnlockUseCase)

    singleOf(::DeeplinkParser)
    singleOf(::ChartIndicatorStorageImpl) bind ChartIndicatorStorage::class
    singleOf(::AndroidBalancePlatformActions) bind BalancePlatformActions::class
    factoryOf(::AndroidHardwareWalletKeyProvisioner) bind HardwareWalletKeyProvisioner::class
    singleOf(::AppPagesImpl) binds arrayOf(
        AppPages::class,
        WalletPages::class,
        MarketPages::class,
        WalletPlatformPages::class,
        WalletSendPages::class,
    )
    single { MarketFeatureConfig(AppConfigProvider.guidesUrl) }
    singleOf(::BalanceViewTypeManager)
    singleOf(::DeviceFlipDetector)
    singleOf(::BalanceHideOnFlipManager) { createdAtStart() } bind BalanceHideOnFlipHandler::class
    singleOf(::TorManager) bind ITorManager::class
    single {
        KeyStoreCleaner(get(), get(), get(), get(), lazy { get<IAccountCleaner>() }, lazy { get<TonConnectManager>() })
    } bind IKeyStoreCleaner::class
    single {
        KeyStoreManager(
            keyAlias = "MASTER_KEY",
            keyStoreCleaner = get(),
            logger = AppLogger(KEY_STORE_LOG_SCOPE)
        )
    }
    single<IKeyStoreManager> { get<KeyStoreManager>() }
    single<IKeyProvider> { get<KeyStoreManager>() }
    singleOf(::EncryptionManager) bind IEncryptionManager::class
    singleOf(::SharedPreferencesBeamResetMarker) bind BeamResetMarker::class
    single {
        // Expose only encryption: this instance must never use shared sample validation.
        val beamEncryption = EncryptionManager(
            KeyStoreManager(
                keyAlias = "BEAM_DATABASE_KEY",
                keyStoreCleaner = get(),
                logger = AppLogger(KEY_STORE_LOG_SCOPE),
            )
        )
        BeamDatabaseKeyProvider(
            context = get(), storageLocator = get(), encryptionManager = beamEncryption, deletionState = get(),
        )
    } bind BeamKitDatabaseKeyProvider::class
    single {
        TonConnectManager(
            context = get(),
            adapterFactory = get(),
            appName = "P.cash Wallet",
            appVersion = AppConfigProvider.appVersion,
            databaseKeyProvider = get(),
            accountManager = get(),
            appDatabase = get(),
            dispatcherProvider = get(),
        ).also { it.start() }
    }
    factory { (pinComponent: IPinComponent) ->
        HiddenWalletPinPolicy(pinComponent, get())
    }

    single(named(PREMIUM_API_BASE_URL_QUALIFIER)) { AppConfigProvider.premiumApiBaseUrl }
    single(named(PREMIUM_IS_DEBUG_QUALIFIER)) { AppConfigProvider.isDebug }

    // Software update (MOBILE-378)
    single {
        GithubApiConfig(
            apiBaseUrl = AppConfigProvider.githubApiBaseUrl,
            rawBaseUrl = AppConfigProvider.githubRawBaseUrl,
            apiProxyBaseUrl = AppConfigProvider.githubApiProxyBaseUrl,
            rawProxyBaseUrl = AppConfigProvider.githubRawProxyBaseUrl,
        )
    }
    singleOf(::SystemTimeProvider) bind TimeProvider::class
    singleOf(::InstallSourceProviderImpl) bind InstallSourceProvider::class
    singleOf(::AppUpdateChecker)
    singleOf(::CheckAppUpdateUseCase)
    singleOf(::ShouldAutoCheckUseCase)
    singleOf(::GetVersionHistoryUseCase)
    singleOf(::GetReleaseChangelogUseCase)

    // Market favorites
    singleOf(::MarketWidgetManager)
    singleOf(::MarketWidgetRepository)

    singleOf(::WidgetMarketFavoritesChangeListener) bind MarketFavoritesChangeListener::class
    single<MarketFavoritesMigration> {
        MarketFavoritesDataMigration(get<AppDatabase>().marketFavoritesDao(), get())
    }
    singleOf(::AssetFiatRateService)

    // Guides
    single { GuidesManager }
    singleOf(::GuidesRepository)

    // Pending transactions
    singleOf(::OfflineQrCodeSaver)
    singleOf(::OfflineTransactionFileTransfer)
    singleOf(::BeamOfflineTransactionRelay)
}
