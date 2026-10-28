package cash.p.terminal.core

import cash.p.terminal.entities.AppVersion
import cash.p.terminal.entities.SyncMode
import cash.p.terminal.shared.main.MainTabStorage
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.modules.premium.settings.PollingInterval
import cash.p.terminal.modules.softwareupdate.domain.UpdateCheckInterval
import cash.p.terminal.modules.market.favorites.WatchlistSorting
import cash.p.terminal.modules.calculator.domain.CalculatorAutoLockOption
import cash.p.terminal.modules.settings.appearance.AppIcon
import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import cash.p.terminal.wallet.Derivation
import io.horizontalsystems.core.BaseCurrencyStorage
import io.horizontalsystems.core.ILoggingSettings
import io.horizontalsystems.core.ISmsNotificationSettings
import kotlinx.coroutines.flow.StateFlow

interface ILocalStorage : ILoggingSettings, ISmsNotificationSettings, BaseCurrencyStorage, WalletOnboardingStorage,
    AppearanceSettingsStore, MainTabStorage,
    BalanceSettingsStorage, TransactionSettingsStorage, NetworkSettingsStorage {
    var marketSearchRecentCoinUids: List<String>

    var autoLockInterval: AutoLockInterval

    var chartIndicatorsEnabled: Boolean
    var authToken: String?
    val appId: String?

    var baseBitcoinProvider: String?
    var baseLitecoinProvider: String?
    var baseDogecoinProvider: String?
    var baseEthereumProvider: String?
    var baseDashProvider: String?
    var baseBinanceProvider: String?
    var baseZcashProvider: String?
    var syncMode: SyncMode?
    var appVersions: List<AppVersion>
    var isAlertNotificationOn: Boolean
    var encryptedSampleText: String?
    var bitcoinDerivation: Derivation?
    override var torEnabled: Boolean
    var appLaunchCount: Int
    var rateAppLastRequestTime: Long
    var balanceHideOnFlipEnabled: Boolean
    var balanceHideOnFlipInfoSuppressed: Boolean
    var securityFlipFeatureSeen: Boolean

    var notificationId: String?
    var notificationServerTime: Long
    var changelogShownForAppVersion: String?
    var ignoreRootedDeviceWarning: Boolean
    var appIcon: AppIcon?
    val appIconRaw: String?
    var isCalculatorModeEnabled: Boolean
    val isCalculatorModeEnabledFlow: StateFlow<Boolean>
    var calculatorModeCreatedPin: Boolean
    var calculatorModeLauncherAliasUpdatePending: Boolean
    var previousAppIconName: String?
    var calculatorThrottleTokens: Int
    var calculatorThrottleLastUptime: Long
    var calculatorThrottleLastWallClock: Long
    var calculatorAutoLockOption: CalculatorAutoLockOption
    val calculatorAutoLockOptionFlow: StateFlow<CalculatorAutoLockOption>
    var marketFavoritesSorting: WatchlistSorting?
    var marketFavoritesShowSignals: Boolean
    var marketFavoritesPeriod: TimeDuration?
    var relaunchBySettingChange: Boolean
    var personalSupportEnabled: Boolean
    var pinRandomized: Boolean
    var statsLastSyncTime: Long
    var shareCrashDataEnabled: Boolean
    var showChangelogAfterUpdate: Boolean
    var selectBalanceTabOnNextLaunch: Boolean

    var updateCheckInterval: UpdateCheckInterval
    var lastUpdateCheckTimestamp: Long
    var latestKnownVersion: String?

    var isSystemPinRequired: Boolean
    // Login Logging properties inherited from ILoggingSettings


    var pushNotificationsEnabled: Boolean
    var pushPollingInterval: PollingInterval
    var pushRealtimeFallbackPollingActive: Boolean
    var pushEnabledBlockchainUids: Set<String>
    var pushBlockchainsConfigured: Boolean
    var pushShowBlockchainName: Boolean
    var pushShowCoinAmount: Boolean
    var pushShowFiatAmount: Boolean

    fun clear()
}
