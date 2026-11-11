package cash.p.terminal.core.storage.desktop

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cash.p.terminal.core.storage.AccountDaos
import cash.p.terminal.core.storage.FavoriteCoin
import cash.p.terminal.core.storage.LevelDaos
import cash.p.terminal.core.storage.SharedDaos
import cash.p.terminal.core.storage.typeconverter.DatabaseConverters
import cash.p.terminal.entities.ActiveAccount
import cash.p.terminal.entities.AddressLabel
import cash.p.terminal.entities.BlockchainSettingRecord
import cash.p.terminal.entities.EnabledWalletCache
import cash.p.terminal.entities.EvmMethodLabel
import cash.p.terminal.entities.EvmSyncSourceRecord
import cash.p.terminal.entities.LocallyCreatedTransactionRecord
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.entities.OfflineBlockchain
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.PendingMultiSwap
import cash.p.terminal.entities.PendingTransactionEntity
import cash.p.terminal.entities.PoisonAddress
import cash.p.terminal.entities.RecentAddress
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.terminal.entities.SpamAddress
import cash.p.terminal.entities.SpamScanState
import cash.p.terminal.entities.StackingCache
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.SyncerState
import cash.p.terminal.entities.TokenAutoEnabledBlockchain
import cash.p.terminal.entities.UserDeletedWallet
import cash.p.terminal.entities.ZcashSingleUseAddress
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.pin.core.Pin
import cash.p.terminal.modules.walletconnect.storage.WalletConnectV2Session
import cash.p.terminal.wallet.entities.AccountRecord
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.HardwarePublicKey
import io.horizontalsystems.core.storage.LogEntry

/** One account's data: it never moves, whichever level the account is visible from. */
@Database(
    version = 1,
    exportSchema = false,
    entities = [
        AccountRecord::class,
        EnabledWallet::class,
        EnabledWalletCache::class,
        RestoreSettingRecord::class,
        WalletConnectV2Session::class,
        TokenAutoEnabledBlockchain::class,
        SpamAddress::class,
        SpamScanState::class,
        SwapProviderTransaction::class,
        HardwarePublicKey::class,
        RecentAddress::class,
        MoneroFileRecord::class,
        PendingMultiSwap::class,
        PendingTransactionEntity::class,
        ZcashSingleUseAddress::class,
        UserDeletedWallet::class,
        PoisonAddress::class,
        LocallyCreatedTransactionRecord::class,
        OfflineSignedTransactionEntity::class,
        OfflineBlockchain::class,
        AccountStoreMeta::class,
        AccountSetting::class,
    ],
)
@TypeConverters(DatabaseConverters::class, VaultConverters::class)
abstract class AccountDatabase : RoomDatabase(), AccountDaos {
    abstract fun accountStoreDao(): AccountStoreDao
}

/** The safe of one level: which accounts it sees and how to reach the level below. */
@Database(
    version = 1,
    exportSchema = false,
    entities = [
        ActiveAccount::class,
        StackingCache::class,
        LogEntry::class,
        AccountPointer::class,
        LevelSetting::class,
        VaultSafeMeta::class,
    ],
)
@TypeConverters(DatabaseConverters::class, VaultConverters::class)
abstract class LevelDatabase : RoomDatabase(), LevelDaos {
    abstract fun vaultSafeDao(): VaultSafeDao
}

@Database(
    version = 1,
    exportSchema = false,
    entities = [
        BlockchainSettingRecord::class,
        EvmSyncSourceRecord::class,
        FavoriteCoin::class,
        ChartIndicatorSetting::class,
        AddressLabel::class,
        EvmMethodLabel::class,
        SyncerState::class,
        Pin::class,
    ],
)
@TypeConverters(DatabaseConverters::class)
abstract class SharedDatabase : RoomDatabase(), SharedDaos
