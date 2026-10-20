package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.favorites.MarketFavoritesManager

/** The common wallet backup with this app's settings and contacts. */
class BackupProvider(
    walletStorage: IEnabledWalletStorage,
    settingsManager: RestoreSettingsManager,
    accountManager: IAccountManager,
    accountFactory: IAccountFactory,
    walletManager: IWalletManager,
    restoreSettingsManager: RestoreSettingsManager,
    marketFavoritesManager: MarketFavoritesManager,
    appSettingsBackup: AndroidAppSettingsBackup,
) : WalletBackupProvider(
    walletStorage = walletStorage,
    settingsManager = settingsManager,
    accountManager = accountManager,
    accountFactory = accountFactory,
    walletManager = walletManager,
    restoreSettingsManager = restoreSettingsManager,
    marketFavoritesManager = marketFavoritesManager,
    appSettingsBackup = appSettingsBackup,
)
