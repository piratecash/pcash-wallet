package cash.p.terminal.feature.wallet.di

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.IBackupManager
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.adapters.zcash.ZcashAddressDeriver
import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.factories.AccountFactory
import cash.p.terminal.core.managers.BackupManager
import cash.p.terminal.core.managers.ChainAccountManagersStarter
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.core.managers.LitecoinBirthdayProvider
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.StellarAccountManager
import cash.p.terminal.core.managers.TermsManager
import cash.p.terminal.core.managers.ThorchainAccountManagers
import cash.p.terminal.core.managers.TimePasswordProvider
import cash.p.terminal.core.managers.TokenAutoEnableManager
import cash.p.terminal.core.managers.TonAccountManager
import cash.p.terminal.core.managers.TronAccountManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.core.managers.WordsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.providers.PredefinedBlockchainSettingsProvider
import cash.p.terminal.core.providers.ZcashDatabaseFilesMarker
import cash.p.terminal.core.providers.ZcashDbMarker
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
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.core.module.dsl.viewModel
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.ethereumkit.ens.EnsResolver
import io.horizontalsystems.ethereumkit.models.RpcSource
import java.net.URI
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.address.AddressCheckManager
import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.managers.AccountCleaner
import cash.p.terminal.core.managers.AccountStorageCleaner
import cash.p.terminal.core.managers.AdapterManager
import cash.p.terminal.core.managers.DeletedAccountsCleanup
import cash.p.terminal.core.managers.BeamAccountDeletionPreflight
import cash.p.terminal.core.managers.BeamDeletionState
import cash.p.terminal.core.managers.BeamKitDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamLifecycleCoordinator
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSessionFactory
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.BeamStorageLocator
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.core.managers.NftAdapterManager
import cash.p.terminal.core.managers.NftMetadataSyncer
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.managers.WalletServicesStarter
import cash.p.terminal.core.providers.ZcashFallbackAddressProvider
import cash.p.terminal.core.storage.SpamAddressStorage
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.core.adapters.zcash.ZcashIronwoodMigrationRegistry
import cash.p.terminal.core.converters.PendingTransactionConverter
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.BaseTokenManager
import cash.p.terminal.core.managers.CoinManager
import cash.p.terminal.core.factories.EvmAccountManagerFactory
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.BitcoinKitConnectionManager
import cash.p.terminal.core.managers.BitcoinKitDatabaseManager
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.SolanaWalletManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.ThorchainKitManagers
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.core.managers.BitcoinKitDatabaseOperations
import cash.p.terminal.core.managers.DefaultBitcoinKitDatabaseOperations
import io.horizontalsystems.bitcoincore.core.IConnectionManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.EvmSignerFactory
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.NftMetadataManager
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.modules.send.beam.BeamOfflineOperations
import cash.p.terminal.modules.send.offline.OfflineSignedTransactionsViewModel
import cash.p.terminal.core.managers.PendingBalanceCalculator
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.PendingTransactionRegistrarImpl
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.providers.FeeRateProvider
import cash.p.terminal.core.providers.PendingAccountProvider
import cash.p.terminal.core.providers.PendingAccountProviderImpl
import cash.p.terminal.core.storage.LocallyCreatedTransactionStorage
import cash.p.terminal.core.storage.NftStorage
import cash.p.terminal.core.storage.OfflineModeStorage
import cash.p.terminal.core.storage.PendingTransactionStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.IDeletedWalletRestorer
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.managers.ITransactionHiddenManager
import io.horizontalsystems.core.PriceChangeIntervalProvider
import cash.p.terminal.network.evmlabel.api.EvmLabelApi
import cash.p.terminal.network.addresscheck.api.HashDitApi
import cash.p.terminal.network.addresscheck.api.ChainalysisApi
import cash.p.terminal.network.alphaaml.api.AlphaAmlApi
import cash.p.terminal.core.address.HashDitAddressValidator
import cash.p.terminal.core.address.ChainalysisAddressValidator
import cash.p.terminal.core.address.AlphaAmlAddressValidator
import cash.p.terminal.core.address.Eip20AddressValidator
import cash.p.terminal.modules.address.AddressHandlerFactory
import cash.p.terminal.modules.contacts.ChooseContactViewModel
import cash.p.terminal.modules.contacts.Mode
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.contacts.model.ContactAddress
import cash.p.terminal.modules.contacts.viewmodel.AddressViewModel
import cash.p.terminal.modules.contacts.viewmodel.ContactViewModel
import cash.p.terminal.modules.contacts.viewmodel.ContactsViewModel
import cash.p.terminal.modules.settings.addresschecker.AddressCheckerViewModel
import cash.p.terminal.modules.settings.addresschecker.UnifiedAddressCheckerViewModel
import cash.p.terminal.modules.settings.addresschecker.ui.AddressCheckBlockchainSelectorViewModel
import cash.p.terminal.modules.settings.addresschecker.ui.AddressCheckTokenSelectorViewModel
import cash.p.terminal.modules.contacts.ContactsSettingsBackup
import cash.p.terminal.wallet.MarketApiConfig
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusViewModel
import cash.p.terminal.modules.offline.OfflineModeToggleViewModel
import cash.p.terminal.modules.syncerror.SyncErrorService
import cash.p.terminal.modules.syncerror.SyncErrorViewModel
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.usecase.SyncPendingMultiSwapUseCase
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.modules.addtoken.AddTokenService
import cash.p.terminal.modules.addtoken.AddTokenViewModel
import cash.p.terminal.modules.configuredtoken.ConfiguredTokenInfoViewModel
import cash.p.terminal.modules.premium.settings.PremiumSettingsViewModel
import cash.p.terminal.modules.receive.FullCoinsProvider
import cash.p.terminal.modules.receive.ReceiveStellarAssetViewModel
import cash.p.terminal.modules.receive.activateTokenViewModel
import cash.p.terminal.modules.receive.receiveAddressViewModel
import cash.p.terminal.modules.receive.viewmodels.BchAddressTypeSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.DerivationSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.NetworkSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.ReceiveMoneroViewModel
import cash.p.terminal.modules.receive.viewmodels.ReceiveSharedViewModel
import cash.p.terminal.modules.receive.viewmodels.ReceiveTokenSelectViewModel
import cash.p.terminal.modules.transactionInfo.transactionInfoViewModel
import cash.p.terminal.modules.transactions.CheckAmlIncomingTransactionUseCase
import cash.p.terminal.modules.transactions.ITransactionRecordRepository
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.SelectContactViewModel
import cash.p.terminal.modules.transactions.TransactionFilterService
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionRecordRepository
import cash.p.terminal.modules.transactions.TransactionSyncStateRepository
import cash.p.terminal.modules.transactions.TransactionViewItemFactory
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.modules.transactions.TransactionsService
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.usecase.GetRestoreHeightForWalletUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.core.usecase.RescanZcashUseCase
import cash.p.terminal.core.usecase.ResolvePayCoreNavigationUseCase
import cash.p.terminal.core.usecase.ResolveTransactionItemUseCase
import cash.p.terminal.modules.balance.BalanceAccountsViewModel
import cash.p.terminal.modules.balance.BalanceAdapterRepository
import cash.p.terminal.modules.balance.BalanceCache
import cash.p.terminal.modules.balance.BalanceSorter
import cash.p.terminal.modules.balance.BalanceViewItemFactory
import cash.p.terminal.modules.balance.balanceViewModel
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewModel
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockViewModel
import cash.p.terminal.modules.balance.token.tokenBalanceViewModel
import cash.p.terminal.modules.coin.overview.TokenWalletActions
import cash.p.terminal.modules.displayoptions.DisplayOptionsViewModel
import cash.p.terminal.modules.managewallets.ManageWalletsTokenWalletActions
import cash.p.terminal.core.adapters.zcash.ZcashSingleUseAddressManager
import cash.p.terminal.core.adapters.zcash.server.ZcashServerProber
import cash.p.terminal.core.adapters.zcash.server.ZcashServerProberImpl
import cash.p.terminal.core.adapters.zcash.server.ZcashServerSelector
import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFailureReporter
import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionManager
import cash.p.terminal.core.adapters.zcash.session.ZcashSyncScheduler
import cash.p.terminal.core.adapters.zcash.session.ZcashWalletOpener
import cash.p.terminal.core.adapters.zcash.session.ZcashWalletOpenerImpl
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.OfflineNetworkController
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.domain.usecase.ClearZCashWalletDataUseCase
import cash.p.terminal.modules.balance.DefaultBalanceService
import cash.p.terminal.modules.balance.DefaultBalanceXRateRepository
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.modules.balance.TotalService
import cash.p.terminal.modules.tokenselect.TokenSelectViewModel
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.balance.BalanceService
import cash.p.terminal.wallet.balance.BalanceXRateRepository
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import cash.p.terminal.di.swapProvidersModule
import cash.p.terminal.modules.paycore.payCoreModule
import org.koin.dsl.bind
import org.koin.dsl.module
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressParserViewModel
import cash.p.terminal.modules.address.NFT_ADDRESS_VIEW_MODEL
import cash.p.terminal.modules.address.nftAddressViewModel
import cash.p.terminal.modules.address.tokenAddressViewModel
import cash.p.terminal.modules.amount.AmountInputViewModel2
import cash.p.terminal.modules.amount.amountInputModeViewModel
import cash.p.terminal.modules.send.address.AddressCheckerControl
import cash.p.terminal.modules.send.address.AddressCheckerControlImpl
import cash.p.terminal.modules.send.beam.beamSendViewModel
import cash.p.terminal.modules.send.bitcoin.advanced.sendBtcAdvancedSettingsViewModel
import cash.p.terminal.modules.send.bitcoin.sendBitcoinViewModel
import cash.p.terminal.modules.send.bitcoin.utxoexpert.utxoExpertModeViewModel
import cash.p.terminal.modules.send.evm.SendEvmData
import cash.p.terminal.modules.send.evm.confirmation.sendEvmConfirmationViewModel
import cash.p.terminal.modules.send.evm.sendEvmViewModel
import cash.p.terminal.modules.send.memo.sendMemoViewModel
import cash.p.terminal.modules.send.monero.sendMoneroViewModel
import cash.p.terminal.modules.send.offline.BeamOfflineTransactionRelay
import cash.p.terminal.modules.send.offline.OfflineBroadcastTokenResolver
import cash.p.terminal.modules.send.offline.OfflineBroadcastViewModel
import cash.p.terminal.modules.send.offline.OfflineTransactionFileTransfer
import cash.p.terminal.modules.transactionInfo.options.SpeedUpCancelType
import cash.p.terminal.modules.transactionInfo.options.transactionSpeedUpCancelViewModel
import cash.p.terminal.modules.transactionInfo.resendbitcoin.resendBitcoinViewModel
import cash.p.terminal.modules.zcashmigration.ZcashMigrationViewModel
import cash.p.terminal.modules.xrate.XRateService
import io.horizontalsystems.core.CurrencyManager
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.wallet.transaction.TransactionSource
import cash.p.terminal.modules.send.securitycheck.securityCheckViewModel
import cash.p.terminal.modules.send.solana.sendSolanaViewModel
import cash.p.terminal.modules.send.ton.sendTonViewModel
import cash.p.terminal.modules.send.tron.sendTronViewModel
import cash.p.terminal.modules.send.zcash.sendZCashViewModel
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.bitcoincore.storage.UnspentOutputInfo
import io.horizontalsystems.ethereumkit.models.TransactionData

val featureWalletModule = module {
    includes(contractValidatorModule, swapProvidersModule, payCoreModule)
    singleOf(::OfflineModeManager)
    singleOf(::OfflineModeUseCase)
    singleOf(::AdapterFactory)
    singleOf(::AdapterManager)
    singleOf(::TransactionAdapterManager)
    single<IAdapterManager> { get<AdapterManager>() }
    singleOf(::OfflineNetworkController)
    singleOf(::MoneroKitManager)
    singleOf(::ClearZCashWalletDataUseCase)
    singleOf(::ZcashDatabaseFiles)
    singleOf(::ZcashDatabaseFilesMarker) bind ZcashDbMarker::class
    single { ZcashDatabaseFailureReporter(get(), get()) }
    singleOf(::ZcashWalletOpenerImpl) bind ZcashWalletOpener::class
    singleOf(::ZcashServerManager)
    singleOf(::ZcashServerProberImpl) bind ZcashServerProber::class
    singleOf(::ZcashServerSelector)
    singleOf(::ZcashSyncScheduler)
    singleOf(::ZcashSessionManager)
    singleOf(::ZcashSingleUseAddressStorage)
    factory { (accountId: String) -> ZcashSingleUseAddressManager(storage = get(), accountId = accountId) }
    factory { get<AppDatabaseProvider>().database.moneroFileDao() }
    factory { get<AppDatabaseProvider>().database.zcashSingleUseAddressDao() }
    factory { get<AppDatabaseProvider>().database.logsDao() }
    factory<BalanceXRateRepository>(named("wallet")) {
        DefaultBalanceXRateRepository(tag = "wallet", currencyManager = get(), marketKit = get())
    }
    factory<BalanceService>(named("wallet")) { DefaultBalanceService.getInstance("wallet") }
    singleOf(::TronAccountManager)
    singleOf(::TonAccountManager)
    singleOf(::StellarAccountManager)
    singleOf(::ThorchainAccountManagers)
    singleOf(::ChainAccountManagersStarter)
    singleOf(::WalletServicesStarter)
    singleOf(::ZcashFallbackAddressProvider)
    singleOf(::AccountStorageCleaner)
    singleOf(::AccountCleaner) bind IAccountCleaner::class
    singleOf(::DeletedAccountsCleanup)
    singleOf(::BeamStorageLocator)
    singleOf(::BeamDeletionState)
    single<AccountDeletionPreflight> {
        // Key wrapping depends on KeyStoreCleaner, which itself needs the AccountManager guarded here.
        BeamAccountDeletionPreflight(
            get(), lazy { get<BeamKitDatabaseKeyProvider>() }, get(), get(), get(),
            lazy { get<BeamSessionOwner>() }, lazy { get<IAdapterManager>() },
        )
    }
    single {
        BeamSessionFactory(
            keyProvider = get(), storageLocator = get(), dispatcherProvider = get(), restoreSettingsManager = get(),
        )
    }
    singleOf(::BeamSessionOwner)
    singleOf(::BeamLifecycleCoordinator)
    singleOf(::BeamSendCoordinator)
    singleOf(::SpamAddressStorage)
    singleOf(::SpamManager)
    singleOf(::AddressCheckManager)
    singleOf(::NftAdapterManager)
    singleOf(::NftMetadataSyncer)
    singleOf(::OfflineOperationGate)
    singleOf(::BackgroundKeepAliveManager)
    singleOf(::NetworkErrorTracker)
    singleOf(::AddressLabelManager)
    singleOf(::AddressMetadataManager)
    singleOf(::EvmLabelManager)
    singleOf(::EvmBlockchainManager)
    singleOf(::BitcoinKitConnectionManager) bind IConnectionManager::class
    singleOf(::DefaultBitcoinKitDatabaseOperations) bind BitcoinKitDatabaseOperations::class
    singleOf(::BitcoinKitDatabaseManager)
    factoryOf(::SolanaWalletManager)
    singleOf(::SolanaKitManager)
    singleOf(::StellarKitManager)
    singleOf(::ThorchainKitManagers)
    singleOf(::TonKitManager)
    singleOf(::TronKitManager)
    singleOf(::EvmSignerFactory)
    factoryOf(::EvmAccountManagerFactory)
    factoryOf(::EvmTransactionRepository)
    singleOf(::NftMetadataManager)
    singleOf(::PoisonAddressManager)
    singleOf(::RecentAddressManager)
    singleOf(::BalanceHiddenManager) bind IBalanceHiddenManager::class
    singleOf(::BaseTokenManager)
    singleOf(::TransactionHiddenManager) bind ITransactionHiddenManager::class
    singleOf(::PriceManager) bind PriceChangeIntervalProvider::class
    singleOf(::LocallyCreatedTransactionRepository)
    singleOf(::PendingTransactionRepository)
    singleOf(::PendingBalanceCalculator)
    singleOf(::PendingTransactionRegistrarImpl) bind PendingTransactionRegistrar::class
    singleOf(::PendingTransactionMatcher)
    singleOf(::PendingTransactionConverter)
    singleOf(::OfflineSignedTransactionRepository)
    singleOf(::OfflineTransactionPayloadEncoder)
    singleOf(::BeamOfflineOperations)
    singleOf(::PendingAccountProviderImpl) bind PendingAccountProvider::class
    singleOf(::ZcashIronwoodMigrationRegistry)
    singleOf(::CoinManager) bind ICoinManager::class
    singleOf(::OfflineModeStorage)
    factoryOf(::SwapProviderTransactionsStorage)
    factoryOf(::SwapTransactionMatcher)
    singleOf(::ContactsRepository)
    singleOf(::FeeRateProvider)
    singleOf(::UserDeletedWalletManager) bind IDeletedWalletRestorer::class
    singleOf(::PendingTransactionStorage)
    singleOf(::LocallyCreatedTransactionStorage)
    factoryOf(::NftStorage)
    single { EvmLabelApi(get(), get<MarketApiConfig>().baseUrl) }
    single { HashDitApi(get(), SharedAppConfig.hashDitBaseUrl, SharedAppConfig.hashDitApiKey) }
    single { ChainalysisApi(get(), SharedAppConfig.chainalysisBaseUrl, SharedAppConfig.chainalysisApiKey) }
    single { AlphaAmlApi(get(), SharedAppConfig.alphaAmlBaseUrl, SharedAppConfig.alphaAmlApiKey) }
    singleOf(::HashDitAddressValidator)
    singleOf(::ChainalysisAddressValidator)
    singleOf(::AlphaAmlAddressValidator)
    singleOf(::ContactsSettingsBackup)
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
    viewModel { (wallet: Wallet) ->
        OfflineModeToggleViewModel(
            wallet = wallet,
            offlineModeManager = get(),
            offlineModeUseCase = get(),
            walletManager = get(),
            adapterManager = get(),
        )
    }
    viewModel { (mode: Mode) -> ContactsViewModel(get(), mode) }
    viewModel { (contact: Contact?, newAddress: ContactAddress?) -> ContactViewModel(get(), contact, newAddress) }
    viewModel { (contactUid: String?, contactAddress: ContactAddress?, definedAddresses: List<ContactAddress>?) ->
        AddressViewModel(
            contactUid = contactUid,
            contactsRepository = get(),
            addressHandlerFactory = AddressHandlerFactory(get<AddressResolutionConfig>().udnApiKey),
            marketKit = get(),
            contactAddress = contactAddress,
            definedAddresses = definedAddresses,
        )
    }
    viewModel { (blockchainType: BlockchainType) -> ChooseContactViewModel(get(), blockchainType) }
    viewModelOf(::AddressCheckerViewModel)
    viewModelOf(::AddressCheckBlockchainSelectorViewModel)
    viewModel { (blockchain: Blockchain?) -> AddressCheckTokenSelectorViewModel(get(), blockchain) }
    viewModel { (initialAddress: String?) ->
        UnifiedAddressCheckerViewModel(
            marketKitWrapper = get(),
            addressHandlerFactory = AddressHandlerFactory(get<AddressResolutionConfig>().udnApiKey),
            hashDitValidator = get(),
            chainalysisValidator = get(),
            eip20Validator = Eip20AddressValidator(get()),
            alphaAmlValidator = get(),
            initialAddress = initialAddress,
        )
    }
    viewModel { (provider: BlockchainStatusProvider) -> BlockchainStatusViewModel(provider, get(), get(), get()) }
    viewModel { (wallet: Wallet) ->
        SyncErrorViewModel(SyncErrorService(wallet, get(), SharedAppConfig.reportEmail, get(), get(), get(), get()))
    }
    singleOf(::CheckAmlIncomingTransactionUseCase)
    singleOf(::AmlStatusManager)
    singleOf(::PendingMultiSwapStorage)
    singleOf(::UpdateSwapProviderTransactionsStatusUseCase)
    singleOf(::SyncPendingMultiSwapUseCase)
    factoryOf(::TransactionRecordRepository) bind ITransactionRecordRepository::class
    factoryOf(::TransactionViewItemFactory)
    factoryOf(::TransactionsRateRepository)
    // Per-screen: the token screen owns its repository and clear()s it, so a shared instance
    // would let one closing screen cancel the sync monitoring of another still open.
    factoryOf(::TransactionSyncStateRepository)
    factoryOf(::NftMetadataService)
    factoryOf(::TransactionsService)
    factoryOf(::TransactionFilterService)
    viewModelOf(::TransactionsViewModel)
    viewModelOf(::OfflineSignedTransactionsViewModel)
    viewModel { (transactionItem: TransactionItem) -> transactionInfoViewModel(transactionItem) }
    viewModel { (selected: Contact?, blockchainType: BlockchainType?) ->
        SelectContactViewModel(get(), selected, blockchainType)
    }
    viewModel { PremiumSettingsViewModel(get(), get(), getOrNull(), get()) }
    factoryOf(::AddTokenService)
    viewModelOf(::AddTokenViewModel)
    viewModel { (token: Token) -> ConfiguredTokenInfoViewModel(token, get(), get()) }
    viewModelOf(::ReceiveSharedViewModel)
    viewModel { (wallet: Wallet) -> receiveAddressViewModel(wallet) }
    viewModel { (wallet: Wallet) -> ReceiveMoneroViewModel(wallet, get(), get(), get()) }
    viewModel { (wallet: Wallet) -> ReceiveStellarAssetViewModel(wallet, get()) }
    viewModel { (wallet: Wallet) -> activateTokenViewModel(wallet) }
    viewModel { (activeAccount: Account, fullCoin: FullCoin) -> NetworkSelectViewModel(activeAccount, fullCoin, get()) }
    viewModel { (activeAccount: Account) ->
        ReceiveTokenSelectViewModel(get(), activeAccount, FullCoinsProvider(get(), activeAccount), get())
    }
    factoryOf(::ResolveTransactionItemUseCase)
    factoryOf(::ResolvePayCoreNavigationUseCase)
    factoryOf(::GetRestoreHeightForWalletUseCase)
    factoryOf(::RescanMoneroUseCase)
    factoryOf(::RescanZcashUseCase)
    factory { BalanceCache(get<AppDatabaseProvider>().database.enabledWalletsCacheDao()) }
    factoryOf(::BalanceAdapterRepository)
    factoryOf(::BalanceViewItemFactory)
    factoryOf(::ManageWalletsTokenWalletActions) bind TokenWalletActions::class
    factoryOf(::TotalService)
    factoryOf(::TotalBalance)
    viewModel { balanceViewModel() }
    viewModel { (wallet: Wallet) -> tokenBalanceViewModel(wallet) }
    viewModel { (blockchainTypes: List<BlockchainType>?, tokenTypes: List<TokenType>?) ->
        TokenSelectViewModel(
            service = DefaultBalanceService.getInstance("wallet"),
            balanceViewItemFactory = get(),
            balanceViewTypeManager = get(),
            itemsFilter = { !it.wallet.account.isWatchAccount },
            balanceSorter = BalanceSorter(),
            balanceHiddenManager = get(),
            blockchainTypes = blockchainTypes,
            tokenTypes = tokenTypes,
            totalBalance = get(),
        )
    }
    viewModelOf(::BalanceAccountsViewModel)
    viewModelOf(::DisplayOptionsViewModel)
    viewModel { (coinUid: String, isPirate: Boolean, blockchainType: BlockchainType) ->
        AddressPoisoningViewModel(coinUid, isPirate, blockchainType, get(), get(), get(), get())
    }
    viewModel { (wallet: Wallet) -> CreationBlockViewModel(wallet, get(), get(), get(), get(), get()) }
    viewModel { (coinUid: String) -> BchAddressTypeSelectViewModel(coinUid, get()) }
    viewModel { (coinUid: String) -> DerivationSelectViewModel(coinUid, get()) }
    viewModel { (termTitles: Array<String>) -> PassphraseTermsViewModel(termTitles, get()) }
    viewModel { (mode: SafetyRulesModule.SafetyRulesMode, termTitles: List<String>) ->
        SafetyRulesViewModel(mode, termTitles, get())
    }
    factoryOf(::AddressCheckerControlImpl) bind AddressCheckerControl::class
    singleOf(::OfflineBroadcastTokenResolver)
    singleOf(::BeamOfflineTransactionRelay)
    singleOf(::OfflineTransactionFileTransfer)
    viewModelOf(::OfflineBroadcastViewModel)
    viewModel { (coinUid: String) -> amountInputModeViewModel(coinUid) }
    viewModel { params -> AmountInputViewModel2(params[0], params[1], params[2], params[3]) }
    viewModel { (token: Token, prefilledData: PrefilledData?) ->
        AddressParserViewModel(AddressUriParser(token.blockchainType, token.type), prefilledData)
    }
    viewModel { (tokenQuery: TokenQuery, coinCode: String, initial: Address?) ->
        tokenAddressViewModel(tokenQuery, coinCode, initial)
    }
    viewModel(named(NFT_ADDRESS_VIEW_MODEL)) { (blockchainType: BlockchainType) -> nftAddressViewModel(blockchainType) }
    viewModel { params -> sendBitcoinViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { params -> sendZCashViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { params -> sendEvmViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { params -> sendSolanaViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { params -> sendTonViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { params -> sendTronViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { params -> sendMoneroViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { params -> sendMemoViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { params -> beamSendViewModel(params[0], params[1], params[2], params[3], params[4]) }
    viewModel { (blockchainType: BlockchainType) -> sendBtcAdvancedSettingsViewModel(blockchainType) }
    viewModel { (adapter: ISendBitcoinAdapter, token: Token, customUnspentOutputs: List<UnspentOutputInfo>?) ->
        utxoExpertModeViewModel(adapter, token, customUnspentOutputs)
    }
    viewModel { (token: Token, address: String, addressCheckerSkippable: Boolean) ->
        securityCheckViewModel(token, address, addressCheckerSkippable)
    }
    viewModel { (transactionData: TransactionData, additionalInfo: SendEvmData.AdditionalInfo?,
            blockchainType: BlockchainType) ->
        sendEvmConfirmationViewModel(transactionData, additionalInfo, blockchainType)
    }
    viewModel { params -> transactionSpeedUpCancelViewModel(params[0], params[1], params[2], params[3]) }
    viewModel { (optionType: SpeedUpCancelType, transactionRecord: BitcoinTransactionRecord,
            source: TransactionSource) ->
        resendBitcoinViewModel(optionType, transactionRecord, source)
    }
    viewModel { (wallet: Wallet) ->
        ZcashMigrationViewModel(
            wallet = wallet,
            pendingRegistrar = get(),
            numberFormatter = get(),
            adapterManager = get(),
            xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)
        )
    }
}
