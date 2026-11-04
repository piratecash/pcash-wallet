package cash.p.terminal.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import cash.p.terminal.BuildConfig
import cash.p.terminal.core.di.appModule
import cash.p.terminal.core.factories.AccountFactory
import cash.p.terminal.core.notifications.TransactionNotificationCoordinator
import cash.p.terminal.core.notifications.TransactionNotificationManager
import cash.p.terminal.core.managers.AppVersionManager
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.BaseTokenManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.core.managers.ReleaseNotesManager
import cash.p.terminal.core.managers.TokenAutoEnableManager
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.WalletServicesStarter
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.domain.usecase.ClearLegacyZcashDataUseCase
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.strings.helpers.LocaleHelper
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.SubscriptionManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.storage.MarketDatabase
import cash.p.terminal.widgets.MarketWidgetWorker
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import com.getkeepsafe.relinker.ReLinker
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.m2049r.levin.util.NetCipherHelper
import com.m2049r.levin.util.NetCipherHelper.OnStatusChangedListener
import com.m2049r.xmrwallet.model.WalletManager
import com.piratecash.monero.net.MoneroProxy
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import io.horizontalsystems.bitcoincore.core.BitcoinCoreContextInitializer
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ICoreApp
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.IPinSettingsStorage
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLog
import io.horizontalsystems.core.security.KeyStoreManager
import io.horizontalsystems.ethereumkit.core.EthereumKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.logging.Level
import java.util.logging.Logger
import androidx.work.Configuration as WorkConfiguration

class App : CoreApp(), WorkConfiguration.Provider, SingletonImageLoader.Factory {

    companion object : ICoreApp by CoreApp {
        var sqlCipherLoadFailed = false

        val localStorage: ILocalStorage by inject(ILocalStorage::class.java)
        val marketStorage: IMarketStorage by inject(IMarketStorage::class.java)
        val torKitManager: ITorManager by inject(ITorManager::class.java)
        val currencyManager: CurrencyManager by inject(CurrencyManager::class.java)
        val languageManager: LanguageManager by inject(LanguageManager::class.java)

        val blockchainSettingsStorage: BlockchainSettingsStorage by inject(BlockchainSettingsStorage::class.java)
        val evmSyncSourceStorage: EvmSyncSourceStorage by inject(EvmSyncSourceStorage::class.java)
        val wordsManager: WordsManager by inject(WordsManager::class.java)
        val networkManager: INetworkManager by inject(INetworkManager::class.java)

        val walletActivator: WalletActivator by inject(WalletActivator::class.java)
        val tokenAutoEnableManager: TokenAutoEnableManager by inject(TokenAutoEnableManager::class.java)
        val accountManager: IAccountManager by inject(IAccountManager::class.java)
        val userManager: DefaultUserManager by inject(DefaultUserManager::class.java)
        val accountFactory: IAccountFactory by inject(AccountFactory::class.java)
        val zcashBirthdayProvider: ZcashBirthdayProvider by inject(ZcashBirthdayProvider::class.java)

        val connectivityManager: ConnectivityManager by inject(ConnectivityManager::class.java)
        val appDatabase: AppDatabase by inject(AppDatabase::class.java)
        val tonKitManager: TonKitManager by inject(TonKitManager::class.java)
        val numberFormatter: IAppNumberFormatter by inject(IAppNumberFormatter::class.java)
        val rateAppManager: IRateAppManager by inject(IRateAppManager::class.java)
        val coinManager: ICoinManager by inject(ICoinManager::class.java)
        val wcSessionManager: WCSessionManager by inject(WCSessionManager::class.java)
        val wcManager: WCManager by inject(WCManager::class.java)
        val termsManager: ITermsManager by inject(ITermsManager::class.java)
        val marketFavoritesManager: MarketFavoritesManager by inject(MarketFavoritesManager::class.java)
        val marketKit: MarketKitWrapper by inject(MarketKitWrapper::class.java)
        val priceManager: PriceManager by inject(PriceManager::class.java)
        val releaseNotesManager: ReleaseNotesManager by inject(ReleaseNotesManager::class.java)
        val evmBlockchainManager: EvmBlockchainManager by inject(EvmBlockchainManager::class.java)
        val evmLabelManager: EvmLabelManager by inject(EvmLabelManager::class.java)
        val balanceHiddenManager: BalanceHiddenManager by inject(IBalanceHiddenManager::class.java)
        val contactsRepository: ContactsRepository by inject(ContactsRepository::class.java)
        val subscriptionManager: SubscriptionManager by inject(SubscriptionManager::class.java)
        val tonConnectManager: TonConnectManager by inject(TonConnectManager::class.java)
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val walletServicesStarter: WalletServicesStarter by inject(WalletServicesStarter::class.java)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DIAGNOSTIC_LOGGING) {
            Timber.plant(Timber.DebugTree())
            KermitLogger.setLogWriters(platformLogWriter())
            KermitLogger.setMinSeverity(Severity.Debug)
        } else {
            KermitLogger.setLogWriters(emptyList())
        }

        instance = this

        val errorLoading = preloadSqlCipher()
        if (errorLoading != null) {
            showFatalErrorAndExit(errorLoading)
            return
        }

        BitcoinCoreContextInitializer().create(this)

        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
        // The first open creates the catalog and loads the bundled coin list; keep it off the main thread.
        coroutineScope.launch { get<MarketDatabase>() }

        if (!BuildConfig.DIAGNOSTIC_LOGGING) {
            //Disable logging for lower levels in Release build
            Logger.getLogger("").level = Level.SEVERE
        }

        installRxErrorHandler(get())

        // Eager and in this order: construction registers lifecycle callbacks, loads the keystore, starts auto-lock.
        get<IPinSettingsStorage>()
        get<BackgroundManager>()
        get<KeyStoreManager>()
        get<IEncryptionManager>()
        get<ISystemInfoManager>()
        get<IPinComponent>()

        setAppTheme()

        get<BaseTokenManager>()
        get<BalanceViewTypeManager>()

        get<TransactionNotificationManager>().apply {
            createNotificationChannel()
            createServiceNotificationChannel()
        }

        get<TransactionNotificationCoordinator>().start()

        startTasks()

        // Never report from the disposable baseline-profile sandbox: it reuses the
        // production Firebase app_id, so its generation crashes/ANRs must not reach prod.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled =
            !BuildConfig.BASELINE_PROFILE_MODE && localStorage.shareCrashDataEnabled
    }

    /**
     * Preload sqlcipher library to avoid issues with loading it in some devices
     * (Fatal Exception: java.lang.UnsatisfiedLinkError
     * dlopen failed: library "libsqlcipher.so" not found)
     */
    private fun preloadSqlCipher(): Throwable? {
        val libraryName = "sqlcipher"

        // Attempt 1: System.loadLibrary
        try {
            System.loadLibrary(libraryName)
            Timber.i("sqlcipher library loaded via System.loadLibrary")
            return null
        } catch (error: Throwable) {
            Timber.w(error, "System.loadLibrary failed")
        }

        // Attempt 2: ReLinker (handles corrupted/missing extractions)
        try {
            ReLinker.force().loadLibrary(this, libraryName)
            Timber.i("sqlcipher library loaded via ReLinker")
            return null
        } catch (error: Throwable) {
            Timber.e(error, "All sqlcipher loading methods failed")
            return error
        }
    }

    private fun showFatalErrorAndExit(t: Throwable) {
        Timber.e("FATAL: SQLCipher native library could not be loaded")
        logError(t, "FATAL: SQLCipher native library could not be loaded")

        // Set flag so MainActivity knows to redirect to error screen
        sqlCipherLoadFailed = true
    }

    private fun initCipherForMonero() {
        NetCipherHelper.createInstance(this@App)
        val cipherTag = "MoneroNetCipher"
        NetCipherHelper.register(object : OnStatusChangedListener {
            override fun connected() {
                Timber.tag(cipherTag).d("CONNECTED")
                applyMoneroProxy()
            }

            override fun disconnected() {
                Timber.tag(cipherTag).d("DISCONNECTED")
                applyMoneroProxy()
            }

            override fun notInstalled() {
                Timber.tag(cipherTag).d("NOT INSTALLED")
                applyMoneroProxy()
            }

            override fun notEnabled() {
                Timber.tag(cipherTag).d("NOT ENABLED")
                notInstalled()
            }
        })

    }

    private fun applyMoneroProxy() {
        tryOrNull { WalletManager.getInstance().setProxy(MoneroProxy.current()) }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (!response.isSuccessful) {
                    // Don't cache error responses (404, 500, etc.)
                    // This prevents placeholder icons from being shown permanently
                    response.newBuilder()
                        .header("Cache-Control", "no-store")
                        .build()
                } else {
                    response
                }
            }
            .build()

        return ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(SvgDecoder.Factory())
                add(OkHttpNetworkFetcherFactory(okHttpClient))
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    private fun initializeWalletConnectV2() {
        val projectId = AppConfigProvider.walletConnectProjectId
        val serverUrl = "wss://${AppConfigProvider.walletConnectUrl}?projectId=$projectId"
        val connectionType = ConnectionType.AUTOMATIC
        val appMetaData = Core.Model.AppMetaData(
            name = AppConfigProvider.walletConnectAppMetaDataName,
            description = "",
            url = AppConfigProvider.walletConnectAppMetaDataUrl,
            icons = listOf(AppConfigProvider.walletConnectAppMetaDataIcon),
            redirect = null,
        )

        CoreClient.initialize(
            metaData = appMetaData,
            relayServerUrl = serverUrl,
            connectionType = connectionType,
            application = this,
            onError = { error ->
                logError(error.throwable, "CoreClient.initialize error")
            },
        )
        WalletKit.initialize(Wallet.Params.Init(core = CoreClient)) { error ->
            logError(error.throwable, "WalletKit.initialize error")
        }
    }

    private fun setAppTheme() {
        val nightMode = when (localStorage.currentTheme) {
            ThemeType.Light -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeType.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeType.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = if (BuildConfig.DIAGNOSTIC_LOGGING) {
            WorkConfiguration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
        } else {
            WorkConfiguration.Builder()
                .setMinimumLoggingLevel(Log.ERROR)
                .build()
        }

    override fun localizedContext(): Context {
        return localeAwareContext(this)
    }

    override fun attachBaseContext(base: Context) {
        LocaleHelper.restoreLocale(base)
        super.attachBaseContext(localeAwareContext(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        localeAwareContext(this)
    }

    override val isSwapEnabled = SharedAppConfig.isSwapEnabled

    private fun startTasks() {
        coroutineScope.launch {
            initCipherForMonero()

            walletServicesStarter.startChainAccountManagers()

            initializeWalletConnectV2()
            WCDelegate.initialize()

            EthereumKit.init()
            walletServicesStarter.startAdapters()
            launch {
                backgroundManager.stateFlow
                    .filter { it == BackgroundManagerState.EnterForeground }
                    // An exception escaping collectLatest would end this collector for good.
                    .collectLatest { marketKit.sync(tryOrNull { needForceUpdateCoins() } ?: false) }
            }
            rateAppManager.onAppLaunch()
            walletServicesStarter.startNftMetadataSync()
            if (!pinComponent.isPinSet) {
                pinComponent.initDefaultPinLevel()
            }
            walletServicesStarter.scheduleAccountDataCleanup()
            wcSessionManager.start()

            AppVersionManager(get(), localStorage).apply { storeAppVersion() }

            if (MarketWidgetWorker.hasEnabledWidgets(instance)) {
                MarketWidgetWorker.enqueuePeriodicRefresh(instance)
            } else {
                MarketWidgetWorker.cancel(instance)
            }

            walletServicesStarter.syncLabels()
            AppLog.cleanupOldLogs()
            get<ClearLegacyZcashDataUseCase>().invoke()
        }
    }

    /*** Check if we don't have new tokens in the market kit */
    private fun needForceUpdateCoins(): Boolean {
        val hasZcashShielded = marketKit.token(
            TokenQuery(
                BlockchainType.Zcash, TokenType.AddressSpecTyped(AddressSpecType.Shielded)
            )
        ) != null

        if (!hasZcashShielded) return true

        if (marketKit.isTonW5NativeTokenMissing()) return true

        // Check if Tether has a BSC token (virtual USDT BEP-20)
        val tetherFullCoin = marketKit.fullCoins(listOf("tether")).firstOrNull()
        val hasTetherOnBsc = tetherFullCoin?.tokens?.any {
            it.blockchainType == BlockchainType.BinanceSmartChain
        } == true

        return !hasTetherOnBsc
    }
}
