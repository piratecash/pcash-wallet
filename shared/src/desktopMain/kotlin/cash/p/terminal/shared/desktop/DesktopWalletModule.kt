package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.multiswap.providers.SwapProvidersStorage
import cash.p.terminal.ui.compose.BalanceHideOnFlipHandler
import cash.p.terminal.modules.contacts.ContactsFileStorage
import cash.p.terminal.modules.contacts.DeleteContactsPinSettings
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.NetworkSettingsStorage
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.adapters.BitcoinHardwareSigners
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.BeamKitDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamResetMarker
import cash.p.terminal.core.managers.BitcoinKitDatabaseKeyProvider
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainCatalog
import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.core.managers.EvmHardwareSigners
import cash.p.terminal.core.managers.MoneroHardwareGateway
import cash.p.terminal.core.managers.PlatformAccountDataCleaner
import cash.p.terminal.core.managers.WalletServicesStarter
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.core.adapters.zcash.ZcashHardwareSigners
import cash.p.terminal.core.managers.SolanaHardwareSigners
import cash.p.terminal.core.managers.StellarHardwareSigners
import cash.p.terminal.core.managers.TonHardwareSigners
import cash.p.terminal.core.managers.TronHardwareSigners
import cash.p.terminal.core.managers.EvmKitDatabaseKeyProvider
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.KitPlatformContexts
import cash.p.terminal.core.managers.SolanaKitDatabaseKeyProvider
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.managers.StellarKitDatabaseKeyProvider
import cash.p.terminal.core.managers.TonKitDatabaseKeyProvider
import cash.p.terminal.core.managers.TronKitDatabaseKeyProvider
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.providers.ZcashFallbackAddressProvider
import cash.p.terminal.core.NonFatalReporter
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKeyProvider
import cash.p.terminal.shared.desktop.vault.DesktopZcashDbKeyProvider
import cash.p.terminal.wallet.FallbackAddressProvider
import cash.p.terminal.core.storage.AccountsStorage
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EnabledWalletsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.core.storage.RestoreSettingsStorage
import cash.p.terminal.core.usecase.GetMoneroWalletFilesNameUseCase
import cash.p.terminal.core.usecase.MoneroWalletStorage
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.feature.wallet.PinStateInfo
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.feature.wallet.WalletSendPages
import cash.p.terminal.modules.backuplocal.fullbackup.AppSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupProvider
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.blockchainstatus.StatusReportBuildInfo
import cash.p.terminal.modules.blockchainstatus.StatusReportExporter
import cash.p.terminal.modules.settings.appearance.LaunchScreenService
import cash.p.terminal.modules.send.offline.OfflineQrImageSaver
import cash.p.terminal.modules.send.offline.OfflineTransactionFiles
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.premium.data.database.PremiumDatabaseProvider
import cash.p.terminal.shared.desktop.vault.DesktopAppDatabaseProvider
import cash.p.terminal.shared.desktop.vault.DesktopBeamKitDatabaseKeyProvider
import cash.p.terminal.shared.desktop.vault.DesktopBitcoinKitDatabaseKeyProvider
import cash.p.terminal.shared.desktop.vault.DesktopEvmKitDatabaseKeyProvider
import cash.p.terminal.shared.desktop.vault.DesktopKitPlatformContexts
import cash.p.terminal.shared.desktop.vault.DesktopPremiumDatabaseProvider
import cash.p.terminal.shared.desktop.vault.DesktopSolanaKitDatabaseKeyProvider
import cash.p.terminal.shared.desktop.vault.DesktopStellarKitDatabaseKeyProvider
import cash.p.terminal.shared.desktop.vault.DesktopTonKitDatabaseKeyProvider
import cash.p.terminal.shared.desktop.vault.DesktopTronKitDatabaseKeyProvider
import cash.p.terminal.wallet.IAccountsStorage
import cash.p.terminal.wallet.IDeletedWalletChecker
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.modules.balance.BalancePlatformActions
import cash.p.terminal.modules.managewallets.HardwareWalletKeyProvisioner
import cash.p.terminal.wallet.navigation.WalletPages
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.policy.PlatformChainSupport
import cash.p.terminal.wallet.policy.PlatformOperationSupport
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import com.m2049r.xmrwallet.service.MoneroWalletService
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.IThirdKeyboard
import io.horizontalsystems.hdwalletkit.Mnemonic
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/** Storage, wallet and premium seams of the desktop app; the databases come from the open vault session. */
internal val desktopWalletModule = module {
    singleOf(::DesktopAppDatabaseProvider) bind AppDatabaseProvider::class
    singleOf(::DesktopPremiumDatabaseProvider) bind PremiumDatabaseProvider::class
    singleOf(::AccountsStorage) bind IAccountsStorage::class
    singleOf(::EnabledWalletsStorage) bind IEnabledWalletStorage::class
    singleOf(::RestoreSettingsStorage) bind IRestoreSettingsStorage::class
    singleOf(::DesktopWalletOnboardingStorage) bind WalletOnboardingStorage::class
    singleOf(::DesktopMoneroWalletStorage) bind MoneroWalletStorage::class
    single { MoneroWalletService(get<MoneroWalletStorage>().walletsRoot()) }
    singleOf(::Mnemonic)
    singleOf(::DesktopDeletedWalletChecker) bind IDeletedWalletChecker::class
    factoryOf(::GetMoneroWalletFilesNameUseCase) bind IGetMoneroWalletFilesNameUseCase::class
    factoryOf(::DesktopRemoveMoneroWalletFilesUseCase) bind IRemoveMoneroWalletFilesUseCase::class
    // The catalog alone: EvmBlockchainManager opens offline-mode storage, which needs the unlocked vault.
    singleOf(::EvmBlockchainCatalog) bind EvmBlockchains::class
    singleOf(::BlockchainSettingsStorage)
    singleOf(::EvmSyncSourceStorage)
    singleOf(::BtcBlockchainManager)
    singleOf(::EvmSyncSourceManager)
    singleOf(::SolanaRpcSourceManager)
    singleOf(::ThemeService)
    singleOf(::LaunchScreenService)
    singleOf(::BalanceViewTypeManager)
    singleOf(::DesktopAppSettingsBackup) bind AppSettingsBackup::class
    singleOf(::DesktopContactsFileStorage) bind ContactsFileStorage::class
    singleOf(::DesktopBackupFileLauncher) bind BackupFileLauncher::class
    singleOf(::DesktopOfflineTransactionFiles) binds
        arrayOf(OfflineTransactionFiles::class, OfflineQrImageSaver::class)
    singleOf(::WalletBackupProvider)
    single {
        // Lazy: the adapters behind these read the storage, which opens only at the first unlock.
        DesktopWalletSession(
            get(), get(), get(), get(), get(),
            lazy { get<CheckPremiumUseCase>() },
            lazy { get<WalletServicesStarter>() },
            get(),
        )
    }
    single { DesktopBalanceAutoHide(get(), get(), lazy { get<BalanceHiddenManager>() }) }
    single<IHardwarePublicKeyStorage> { DesktopNoHardwareWallets }
    single<HardwareWalletTokenPolicy> { DesktopNoHardwareWallets }
    single<EvmHardwareSigners> { DesktopNoHardwareSigners }
    single<BitcoinHardwareSigners> { DesktopNoHardwareSigners }
    single<SolanaHardwareSigners> { DesktopNoHardwareSigners }
    single<TronHardwareSigners> { DesktopNoHardwareSigners }
    single<StellarHardwareSigners> { DesktopNoHardwareSigners }
    single<TonHardwareSigners> { DesktopNoHardwareSigners }
    single<ZcashHardwareSigners> { DesktopNoHardwareSigners }
    single<MoneroHardwareGateway> { DesktopNoHardwareSigners }
    single<PlatformAccountDataCleaner> { DesktopPlatformAccountDataCleaner }
    single<PinStateInfo> { DesktopPinStateInfo }
    single<DeleteContactsPinSettings> { DesktopDeleteContactsPinSettings }
    single<AddressResolutionConfig> { DesktopAddressResolutionConfig }
    single<IThirdKeyboard> { DesktopThirdKeyboard }
    singleOf(::DesktopBeamResetMarker) bind BeamResetMarker::class
    singleOf(::DesktopWalletPages) binds
        arrayOf(AppPages::class, WalletPages::class, WalletPlatformPages::class, WalletSendPages::class)
    singleOf(::DesktopSecretActionAuthorizer) bind SecretActionAuthorizer::class
    singleOf(::DesktopAppRestarter) bind AppRestarter::class
    singleOf(::DesktopWalletSettingsStorage) binds arrayOf(
        BalanceSettingsStorage::class, TransactionSettingsStorage::class, NetworkSettingsStorage::class,
        SwapProvidersStorage::class,
    )
    single<PlatformChainSupport> { DesktopPlatformChainSupport }
    single<PlatformOperationSupport> { DesktopPlatformOperationSupport }
    single<BalanceHideOnFlipHandler> { DesktopBalanceHideOnFlipHandler }
    single<HardwareWalletKeyProvisioner> { DesktopNoHardwareKeyProvisioner }
    single<ScanToAddUseCase> { DesktopNoHardwareScanToAdd }
    single<BalancePlatformActions> { DesktopBalancePlatformActions }
    singleOf(::DesktopSystemInfoManager) bind ISystemInfoManager::class
    singleOf(::DesktopStatusReportBuildInfo) bind StatusReportBuildInfo::class
    singleOf(::DesktopStatusReportExporter) bind StatusReportExporter::class
    singleOf(::DesktopKitPlatformContexts) bind KitPlatformContexts::class
    singleOf(::DesktopBitcoinKitDatabaseKeyProvider) bind BitcoinKitDatabaseKeyProvider::class
    singleOf(::DesktopTonKitDatabaseKeyProvider) bind TonKitDatabaseKeyProvider::class
    singleOf(::DesktopTronKitDatabaseKeyProvider) bind TronKitDatabaseKeyProvider::class
    singleOf(::DesktopEvmKitDatabaseKeyProvider) bind EvmKitDatabaseKeyProvider::class
    singleOf(::DesktopSolanaKitDatabaseKeyProvider) bind SolanaKitDatabaseKeyProvider::class
    singleOf(::DesktopStellarKitDatabaseKeyProvider) bind StellarKitDatabaseKeyProvider::class
    singleOf(::DesktopZcashDbKeyProvider) bind ZcashDbKeyProvider::class
    singleOf(::DesktopBeamKitDatabaseKeyProvider) bind BeamKitDatabaseKeyProvider::class
    single<NonFatalReporter> { DesktopNonFatalReporter }
    // The TON fallback address provider stays in the Android app; desktop has none for TON.
    single<FallbackAddressProvider> { get<ZcashFallbackAddressProvider>() }
}
