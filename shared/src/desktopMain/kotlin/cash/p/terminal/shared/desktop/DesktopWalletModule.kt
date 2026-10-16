package cash.p.terminal.shared.desktop

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainCatalog
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.providers.ZcashDbMarker
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
import cash.p.terminal.modules.backuplocal.fullbackup.AppSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupProvider
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.settings.appearance.LaunchScreenService
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.premium.data.database.PremiumDatabaseProvider
import cash.p.terminal.shared.desktop.vault.DesktopAppDatabaseProvider
import cash.p.terminal.shared.desktop.vault.DesktopPremiumDatabaseProvider
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.IAccountsStorage
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IDeletedWalletChecker
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.navigation.WalletPages
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import com.m2049r.xmrwallet.service.MoneroWalletService
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
    singleOf(::DesktopAdapterManager) bind IAdapterManager::class
    singleOf(::EvmBlockchainCatalog) bind EvmBlockchains::class
    singleOf(::BlockchainSettingsStorage)
    singleOf(::EvmSyncSourceStorage)
    singleOf(::BtcBlockchainManager)
    singleOf(::EvmSyncSourceManager)
    singleOf(::SolanaRpcSourceManager)
    singleOf(::ZcashServerManager)
    singleOf(::ThemeService)
    singleOf(::LaunchScreenService)
    singleOf(::BalanceViewTypeManager)
    singleOf(::DesktopAppSettingsBackup) bind AppSettingsBackup::class
    singleOf(::DesktopBackupFileLauncher) bind BackupFileLauncher::class
    singleOf(::WalletBackupProvider)
    singleOf(::DesktopWalletSession)
    single<IHardwarePublicKeyStorage> { DesktopNoHardwareWallets }
    single<HardwareWalletTokenPolicy> { DesktopNoHardwareWallets }
    single<PinStateInfo> { DesktopPinStateInfo }
    single<ZcashDbMarker> { DesktopZcashDbMarker }
    single<AddressResolutionConfig> { DesktopAddressResolutionConfig }
    singleOf(::DesktopBalanceHiddenManager) bind IBalanceHiddenManager::class
    single<IThirdKeyboard> { DesktopThirdKeyboard }
    single<AccountDeletionPreflight> { DesktopAccountDeletionPreflight }
    singleOf(::DesktopWalletPages) binds
        arrayOf(AppPages::class, WalletPages::class, WalletPlatformPages::class)
    singleOf(::DesktopSecretActionAuthorizer) bind SecretActionAuthorizer::class
    singleOf(::DesktopAppRestarter) bind AppRestarter::class
}
