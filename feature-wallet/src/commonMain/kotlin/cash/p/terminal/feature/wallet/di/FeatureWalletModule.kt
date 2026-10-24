package cash.p.terminal.feature.wallet.di

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.IBackupManager
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.adapters.zcash.ZcashAddressDeriver
import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.factories.AccountFactory
import cash.p.terminal.core.managers.BackupManager
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.core.managers.LitecoinBirthdayProvider
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.TermsManager
import cash.p.terminal.core.managers.TimePasswordProvider
import cash.p.terminal.core.managers.TokenAutoEnableManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.providers.PredefinedBlockchainSettingsProvider
import cash.p.terminal.core.usecase.GenerateMoneroWalletUseCase
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.usecase.ValidateMoneroMnemonicUseCase
import cash.p.terminal.feature.wallet.LocalizedAssetReader
import cash.p.terminal.modules.settings.appearance.AppearanceViewModel
import cash.p.terminal.modules.settings.terms.TermsViewModel
import cash.p.terminal.core.managers.RandomProvider
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.modules.backupalert.BackupAlertViewModel
import cash.p.terminal.modules.backuplocal.fullbackup.AppearanceSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.BlockchainSettingsBackup
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsService
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsViewModel
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsService
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsViewModel
import cash.p.terminal.modules.evmnetwork.EvmNetworkViewModel
import cash.p.terminal.modules.evmnetwork.addrpc.AddRpcViewModel
import cash.p.terminal.modules.backuplocal.fullbackup.BackupViewItemFactory
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel
import cash.p.terminal.modules.backuplocal.password.BackupLocalPasswordViewModel
import cash.p.terminal.modules.backuplocal.password.BackupType
import cash.p.terminal.modules.createaccount.CreateAdvancedAccountViewModel
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.modules.importwallet.ImportWalletViewModel
import cash.p.terminal.modules.intro.IntroViewModel
import cash.p.terminal.modules.manageaccount.ManageAccountViewModel
import cash.p.terminal.modules.manageaccount.backupconfirmkey.BackupConfirmKeyViewModel
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyViewModel
import cash.p.terminal.modules.manageaccount.privatekeys.PrivateKeysViewModel
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysViewModel
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhrasePage
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseViewModel
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesModule
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesViewModel
import cash.p.terminal.modules.manageaccount.zcashkeys.ZcashKeysViewModel
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsViewModel
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureViewModel
import cash.p.terminal.modules.mwebconfigure.MwebConfigureViewModel
import cash.p.terminal.modules.premium.about.AboutPremiumViewModel
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestoreMnemonicNonStandardViewModel
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKeyViewModel
import cash.p.terminal.modules.restorelocal.RestoreLocalViewModel
import cash.p.terminal.modules.solananetwork.SolanaNetworkService
import cash.p.terminal.modules.solananetwork.SolanaNetworkViewModel
import cash.p.terminal.modules.unlinkaccount.UnlinkAccountViewModel
import cash.p.terminal.modules.watchaddress.WatchAddressModule
import cash.p.terminal.modules.watchaddress.WatchAddressService
import cash.p.terminal.modules.watchaddress.WatchAddressViewModel
import cash.p.terminal.modules.watchaddress.selectblockchains.SelectBlockchainsViewModel
import cash.p.terminal.modules.zcashconfigure.ZcashConfigureViewModel
import cash.p.terminal.modules.zcashnetwork.ZcashNetworkViewModel
import cash.p.terminal.modules.zcashnetwork.addserver.AddZcashServerViewModel
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.PassphraseValidator
import io.horizontalsystems.core.entities.Blockchain
import org.koin.core.module.dsl.viewModel
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.ethereumkit.ens.EnsResolver
import io.horizontalsystems.ethereumkit.models.RpcSource
import java.net.URI
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val featureWalletModule = module {
    singleOf(::DefaultUserManager) bind UserManager::class
    singleOf(::AccountFactory) bind IAccountFactory::class
    singleOf(::TermsManager) bind ITermsManager::class
    singleOf(::BackupManager) bind IBackupManager::class
    factoryOf(::WalletActivator)
    factoryOf(::WordsManager)
    singleOf(::RestoreSettingsManager)
    singleOf(::TimePasswordProvider)
    singleOf(::SeedPhraseQrCrypto)
    singleOf(::TokenAutoEnableManager)
    singleOf(::ZcashAddressDeriver)
    single { EnsResolver(RpcSource.Http(listOf(URI(get<AddressResolutionConfig>().blocksDecodedEthereumRpc)), null)) }
    singleOf(::ZcashKeyExporter)
    singleOf(::ZcashBirthdayProvider)
    singleOf(::LitecoinBirthdayProvider)
    singleOf(::PredefinedBlockchainSettingsProvider)
    factoryOf(::ValidateMoneroMnemonicUseCase)
    factoryOf(::ValidateMoneroHeightUseCase)
    factoryOf(::MoneroWalletUseCase)
    factoryOf(::GenerateMoneroWalletUseCase)
    factoryOf(::LocalizedAssetReader)
    factoryOf(::AppearanceSettingsBackup)
    factoryOf(::BlockchainSettingsBackup)
    viewModelOf(::TermsViewModel)
    viewModelOf(::AppearanceViewModel)
    factoryOf(::WatchAddressService)
    factoryOf(::BlockchainSettingsService)
    viewModelOf(::BlockchainSettingsViewModel)
    factoryOf(::SolanaNetworkService)
    viewModelOf(::SolanaNetworkViewModel)
    viewModelOf(::ZcashNetworkViewModel)
    viewModelOf(::AddZcashServerViewModel)
    viewModel { (blockchain: Blockchain) ->
        BtcBlockchainSettingsViewModel(BtcBlockchainSettingsService(blockchain, get()), get())
    }
    viewModel { (blockchain: Blockchain) -> EvmNetworkViewModel(blockchain, get()) }
    viewModel { (blockchain: Blockchain) -> AddRpcViewModel(blockchain, get()) }

    viewModelOf(::IntroViewModel)
    viewModelOf(::BackupAlertViewModel)
    viewModelOf(::CreateAdvancedAccountViewModel)
    viewModelOf(::RestoreMnemonicViewModel)
    viewModelOf(::RestoreMnemonicNonStandardViewModel)
    viewModelOf(::RestorePrivateKeyViewModel)
    viewModelOf(::MoneroConfigureViewModel)
    viewModelOf(::MwebConfigureViewModel)
    viewModelOf(::ZcashConfigureViewModel)
    viewModelOf(::AboutPremiumViewModel)
    viewModelOf(::ImportWalletViewModel)
    viewModel { SelectBackupItemsViewModel(get(), BackupViewItemFactory()) }
    viewModel { (backupType: BackupType) ->
        BackupLocalPasswordViewModel(backupType, PassphraseValidator(), get(), get(), get())
    }
    viewModel { (backupFilePath: String, fileName: String?) ->
        RestoreLocalViewModel(backupFilePath, get(), get(), BackupViewItemFactory(), get(), fileName)
    }
    viewModel {
        WatchAddressViewModel(get(), WatchAddressModule.addressParserChain(get<AddressResolutionConfig>().udnApiKey))
    }
    viewModel { (accountType: AccountType, accountName: String?) ->
        SelectBlockchainsViewModel(accountType, accountName, get())
    }
    viewModel { (account: Account) -> ManageAccountViewModel(account, get()) }
    viewModel { (account: Account) -> UnlinkAccountViewModel(account, get()) }
    viewModel { (account: Account) -> PrivateKeysViewModel(account, get()) }
    viewModel { (account: Account) -> PublicKeysViewModel(account, get()) }
    viewModel { (account: Account) -> BackupConfirmKeyViewModel(account, get(), RandomProvider()) }
    viewModel { (account: Account, type: RecoveryPhrasePage.RecoveryPhraseType) ->
        RecoveryPhraseViewModel(account, type, get(), get(), get())
    }
    viewModel { (accountToCopy: Account) ->
        DuplicateWalletViewModel(accountToCopy, get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { (accountId: String) -> BackupKeyViewModel(accountId, get()) }
    viewModel { (accountId: String) -> ZcashKeysViewModel(accountId, get(), get()) }
    viewModel { (mode: ManageAccountsModule.Mode) -> ManageAccountsViewModel(get(), get(), mode) }
    viewModel { (termTitles: Array<String>) -> PassphraseTermsViewModel(termTitles, get()) }
    viewModel { (mode: SafetyRulesModule.SafetyRulesMode, termTitles: List<String>) ->
        SafetyRulesViewModel(mode, termTitles, get())
    }
}
