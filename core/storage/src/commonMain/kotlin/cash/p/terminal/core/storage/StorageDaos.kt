package cash.p.terminal.core.storage

import cash.p.terminal.modules.chart.ChartIndicatorSettingsDao
import cash.p.terminal.modules.pin.core.PinDao
import cash.p.terminal.modules.walletconnect.storage.WCSessionDao
import io.horizontalsystems.core.storage.LogsDao

/** Tables that follow one account. */
interface AccountDaos : AccountSetupDaos, AccountActivityDaos

interface AccountSetupDaos {
    fun accountsDao(): AccountsDao
    fun walletsDao(): EnabledWalletsDao
    fun enabledWalletsCacheDao(): EnabledWalletsCacheDao
    fun restoreSettingDao(): RestoreSettingDao
    fun wcSessionDao(): WCSessionDao
    fun tokenAutoEnabledBlockchainDao(): TokenAutoEnabledBlockchainDao
    fun hardwarePublicKeyDao(): HardwarePublicKeyDao
    fun moneroFileDao(): MoneroFileDao
    fun zcashSingleUseAddressDao(): ZcashSingleUseAddressDao
    fun userDeletedWalletDao(): UserDeletedWalletDao
    fun offlineBlockchainDao(): OfflineBlockchainDao
}

interface AccountActivityDaos {
    fun spamAddressDao(): SpamAddressDao
    fun swapProviderTransactionsDao(): SwapProviderTransactionsDao
    fun recentAddressDao(): RecentAddressDao
    fun pendingMultiSwapDao(): PendingMultiSwapDao
    fun pendingTransactionDao(): PendingTransactionDao
    fun poisonAddressDao(): PoisonAddressDao
    fun locallyCreatedTransactionDao(): LocallyCreatedTransactionDao
    fun offlineSignedTransactionDao(): OfflineSignedTransactionDao
}

/** Tables that belong to one access level. */
interface LevelDaos {
    fun activeAccountDao(): ActiveAccountDao
    fun logsDao(): LogsDao
    fun stackingCacheDao(): StackingCacheDao
}

/** Tables shared by every level. */
interface SharedDaos {
    fun chartIndicatorSettingsDao(): ChartIndicatorSettingsDao
    fun blockchainSettingDao(): BlockchainSettingDao
    fun evmSyncSourceDao(): EvmSyncSourceDao
    fun marketFavoritesDao(): MarketFavoritesDao
    fun addressLabelDao(): AddressLabelDao
    fun evmMethodLabelDao(): EvmMethodLabelDao
    fun syncerStateDao(): SyncerStateDao
    fun pinDao(): PinDao
}
