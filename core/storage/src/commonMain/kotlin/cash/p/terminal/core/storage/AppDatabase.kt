package cash.p.terminal.core.storage

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import cash.p.terminal.core.storage.typeconverter.DatabaseConverters
import cash.p.terminal.entities.ActiveAccount
import cash.p.terminal.entities.AddressLabel
import cash.p.terminal.entities.BlockchainSettingRecord
import cash.p.terminal.entities.EnabledWalletCache
import cash.p.terminal.entities.OfflineBlockchain
import cash.p.terminal.entities.PoisonAddress
import cash.p.terminal.entities.EvmMethodLabel
import cash.p.terminal.entities.EvmSyncSourceRecord
import cash.p.terminal.entities.LocallyCreatedTransactionRecord
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.PendingMultiSwap
import cash.p.terminal.entities.PendingTransactionEntity
import cash.p.terminal.entities.RecentAddress
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.terminal.entities.SpamAddress
import cash.p.terminal.entities.SpamScanState
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.SyncerState
import cash.p.terminal.entities.TokenAutoEnabledBlockchain
import cash.p.terminal.entities.UserDeletedWallet
import cash.p.terminal.entities.ZcashSingleUseAddress
import cash.p.terminal.entities.nft.NftAssetBriefMetadataRecord
import cash.p.terminal.entities.nft.NftAssetRecord
import cash.p.terminal.entities.nft.NftCollectionRecord
import cash.p.terminal.entities.nft.NftMetadataSyncRecord
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.chart.ChartIndicatorSettingsDao
import cash.p.terminal.modules.pin.core.Pin
import cash.p.terminal.modules.pin.core.PinDao
import cash.p.terminal.modules.walletconnect.storage.WCPairingMetadataDao
import cash.p.terminal.modules.walletconnect.storage.WCSessionDao
import cash.p.terminal.modules.walletconnect.storage.WalletConnectV2PairingMetadata
import cash.p.terminal.modules.walletconnect.storage.WalletConnectV2Session
import cash.p.terminal.wallet.entities.AccountRecord
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.HardwarePublicKey
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao

const val APP_DATABASE_NAME = "db_p_cash"

@Database(
    version = 119,
    exportSchema = false,
    entities = [
        EnabledWallet::class,
        EnabledWalletCache::class,
        AccountRecord::class,
        BlockchainSettingRecord::class,
        EvmSyncSourceRecord::class,
        LogEntry::class,
        FavoriteCoin::class,
        WalletConnectV2Session::class,
        WalletConnectV2PairingMetadata::class,
        RestoreSettingRecord::class,
        ActiveAccount::class,
        NftCollectionRecord::class,
        NftAssetRecord::class,
        NftMetadataSyncRecord::class,
        NftAssetBriefMetadataRecord::class,
        AddressLabel::class,
        EvmMethodLabel::class,
        SyncerState::class,
        TokenAutoEnabledBlockchain::class,
        ChartIndicatorSetting::class,
        Pin::class,
        SwapProviderTransaction::class,
        HardwarePublicKey::class,
        RecentAddress::class,
        SpamAddress::class,
        SpamScanState::class,
        MoneroFileRecord::class,
        PendingMultiSwap::class,
        PendingTransactionEntity::class,
        ZcashSingleUseAddress::class,
        UserDeletedWallet::class,
        PoisonAddress::class,
        LocallyCreatedTransactionRecord::class,
        OfflineSignedTransactionEntity::class,
        OfflineBlockchain::class
    ]
)
@TypeConverters(DatabaseConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chartIndicatorSettingsDao(): ChartIndicatorSettingsDao
    abstract fun walletsDao(): EnabledWalletsDao
    abstract fun enabledWalletsCacheDao(): EnabledWalletsCacheDao
    abstract fun accountsDao(): AccountsDao
    abstract fun blockchainSettingDao(): BlockchainSettingDao
    abstract fun evmSyncSourceDao(): EvmSyncSourceDao
    abstract fun restoreSettingDao(): RestoreSettingDao
    abstract fun logsDao(): LogsDao
    abstract fun marketFavoritesDao(): MarketFavoritesDao
    abstract fun wcSessionDao(): WCSessionDao
    abstract fun wcPairingMetadataDao(): WCPairingMetadataDao
    abstract fun nftDao(): NftDao
    abstract fun addressLabelDao(): AddressLabelDao
    abstract fun evmMethodLabelDao(): EvmMethodLabelDao
    abstract fun syncerStateDao(): SyncerStateDao
    abstract fun tokenAutoEnabledBlockchainDao(): TokenAutoEnabledBlockchainDao
    abstract fun spamAddressDao(): SpamAddressDao
    abstract fun pinDao(): PinDao
    abstract fun swapProviderTransactionsDao(): SwapProviderTransactionsDao
    abstract fun hardwarePublicKeyDao(): HardwarePublicKeyDao
    abstract fun recentAddressDao(): RecentAddressDao
    abstract fun moneroFileDao(): MoneroFileDao
    abstract fun pendingMultiSwapDao(): PendingMultiSwapDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun zcashSingleUseAddressDao(): ZcashSingleUseAddressDao
    abstract fun userDeletedWalletDao(): UserDeletedWalletDao
    abstract fun poisonAddressDao(): PoisonAddressDao
    abstract fun locallyCreatedTransactionDao(): LocallyCreatedTransactionDao
    abstract fun offlineSignedTransactionDao(): OfflineSignedTransactionDao
    abstract fun offlineBlockchainDao(): OfflineBlockchainDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
