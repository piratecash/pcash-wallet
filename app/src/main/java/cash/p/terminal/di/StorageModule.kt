package cash.p.terminal.di

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.managers.DeletedWalletChecker
import cash.p.terminal.core.managers.NumberFormatter
import cash.p.terminal.wallet.IDeletedWalletChecker
import cash.p.terminal.core.storage.AccountsStorage
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.buildAppDatabase
import cash.p.terminal.core.storage.EnabledWalletsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.core.storage.RestoreSettingsStorage
import cash.p.terminal.core.storage.StaticAppDatabaseProvider
import cash.p.terminal.modules.pin.core.PinDbStorage
import cash.p.terminal.wallet.IAccountsStorage
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import io.horizontalsystems.core.IAppNumberFormatter
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val storageModule = module {
    single { buildAppDatabase(get()) }
    singleOf(::StaticAppDatabaseProvider) bind AppDatabaseProvider::class
    singleOf(::NumberFormatter) bind IAppNumberFormatter::class
    singleOf(::RestoreSettingsStorage) bind IRestoreSettingsStorage::class
    singleOf(::AccountsStorage) bind IAccountsStorage::class
    singleOf(::EnabledWalletsStorage) bind IEnabledWalletStorage::class
    singleOf(::HardwarePublicKeyStorage) bind IHardwarePublicKeyStorage::class
    singleOf(::BlockchainSettingsStorage)
    singleOf(::EvmSyncSourceStorage)



    single { get<AppDatabase>().addressLabelDao() }
    single { get<AppDatabase>().evmMethodLabelDao() }
    single { get<AppDatabase>().syncerStateDao() }
    single { get<AppDatabase>().recentAddressDao() }
    single { get<AppDatabase>().userDeletedWalletDao() }
    singleOf(::DeletedWalletChecker) bind IDeletedWalletChecker::class
    single { get<AppDatabase>().poisonAddressDao() }
    single { get<AppDatabase>().pendingTransactionDao() }
    single { get<AppDatabase>().locallyCreatedTransactionDao() }
    single { get<AppDatabase>().swapProviderTransactionsDao() }
    single { get<AppDatabase>().pinDao() }
    single { get<AppDatabase>().nftDao() }
    single { get<AppDatabase>().chartIndicatorSettingsDao() }
    singleOf(::PinDbStorage)
}
