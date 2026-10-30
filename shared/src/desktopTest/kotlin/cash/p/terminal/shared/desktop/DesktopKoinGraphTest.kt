package cash.p.terminal.shared.desktop

import androidx.lifecycle.viewmodel.CreationExtras
import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.managers.BeamAccountDeletionPreflight
import cash.p.terminal.core.managers.BeamKitDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamLifecycleCoordinator
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.MoneroHardwareGateway
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.shared.desktop.vault.DesktopBeamKitDatabaseKeyProvider
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.ThorchainKitManagers
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.managers.WalletServicesStarter
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.usecase.GetRestoreHeightForWalletUseCase
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.modules.balance.BalanceAccountsViewModel
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.contacts.DeleteContactsPinSettings
import cash.p.terminal.modules.managewallets.ManageWalletsModule
import cash.p.terminal.modules.managewallets.ManageWalletsViewModel
import cash.p.terminal.modules.send.offline.OfflineSignedTransactionsViewModel
import cash.p.terminal.modules.settings.addresschecker.AddressCheckerViewModel
import cash.p.terminal.modules.settings.addresschecker.ui.AddressCheckBlockchainSelectorViewModel
import cash.p.terminal.modules.syncerror.SyncErrorViewModel
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.ui.compose.BalanceHideOnFlipHandler
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.policy.PlatformChainSupport
import cash.p.terminal.wallet.policy.PlatformOperationSupport
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.IAppNumberFormatter
import org.koin.core.error.InstanceCreationException
import kotlin.test.assertIs
import kotlin.test.assertTrue
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.modules.premium.settings.PremiumPlatformSettings
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.modules.configuredtoken.ConfiguredTokenInfoViewModel
import cash.p.terminal.modules.transactions.SelectContactViewModel
import cash.p.terminal.modules.receive.viewmodels.DerivationSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.BchAddressTypeSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.ReceiveTokenSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.NetworkSelectViewModel
import cash.p.terminal.modules.receive.ActivateTokenViewModel
import cash.p.terminal.modules.receive.ReceiveStellarAssetViewModel
import cash.p.terminal.modules.receive.viewmodels.ReceiveMoneroViewModel
import cash.p.terminal.modules.receive.viewmodels.ReceiveAddressViewModel
import cash.p.terminal.modules.receive.FullCoinsProvider
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItemFactory
import cash.p.terminal.modules.transactionInfo.TransactionInfoService
import cash.p.terminal.core.IRandomProvider
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.adapters.zcash.ZcashAddressDeriver
import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.managers.LitecoinBirthdayProvider
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.TokenAutoEnableManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.providers.ZcashDbMarker
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.feature.wallet.LocalizedAssetReader
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.feature.wallet.WalletSendPages
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.navigation.WalletPages
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusViewModel
import cash.p.terminal.modules.contacts.ChooseContactViewModel
import cash.p.terminal.modules.contacts.Mode
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.contacts.model.ContactAddress
import cash.p.terminal.modules.contacts.viewmodel.AddressViewModel
import cash.p.terminal.modules.contacts.viewmodel.ContactViewModel
import cash.p.terminal.modules.contacts.viewmodel.ContactsViewModel
import cash.p.terminal.modules.offline.OfflineModeToggleViewModel
import cash.p.terminal.modules.settings.addresschecker.UnifiedAddressCheckerViewModel
import cash.p.terminal.modules.settings.addresschecker.ui.AddressCheckTokenSelectorViewModel
import cash.p.terminal.modules.syncerror.SyncErrorService
import cash.p.terminal.core.storage.EnabledWalletsCacheDao
import cash.p.terminal.modules.balance.DefaultBalanceService
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.modules.balance.token.TokenBalanceService
import cash.p.terminal.modules.balance.token.TokenBalanceViewModel
import cash.p.terminal.modules.balance.token.TokenTransactionsService
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewModel
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockViewModel
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.core.address.Eip20AddressValidator
import cash.p.terminal.modules.address.AddressHandlerFactory
import io.mockk.mockk
import org.koin.core.context.GlobalContext
import cash.p.terminal.feature.market.di.marketFeatureModule
import cash.p.terminal.modules.address.AddressParserChain
import cash.p.terminal.modules.backuplocal.fullbackup.BackupViewItemFactory
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel
import cash.p.terminal.modules.backuplocal.password.BackupLocalPasswordViewModel
import cash.p.terminal.modules.backuplocal.password.BackupType
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsService
import cash.p.terminal.modules.chart.ChartCurrencyValueFormatterShortened
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.coin.CoinService
import cash.p.terminal.modules.settings.appearance.AppearanceViewModel
import cash.p.terminal.modules.evmnetwork.EvmNetworkViewModel
import cash.p.terminal.modules.evmnetwork.addrpc.AddRpcViewModel
import cash.p.terminal.modules.coin.CoinViewFactory
import cash.p.terminal.modules.coin.CoinViewModel
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.RankType
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsService
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsViewModel
import cash.p.terminal.modules.coin.audits.CoinAuditsViewModel
import cash.p.terminal.modules.coin.coinmarkets.CoinMarketsService
import cash.p.terminal.modules.coin.coinmarkets.CoinMarketsViewModel
import cash.p.terminal.modules.coin.detectors.DetectorsViewModel
import cash.p.terminal.modules.coin.indicators.MacdSettingViewModel
import cash.p.terminal.modules.coin.indicators.MovingAverageSettingViewModel
import cash.p.terminal.modules.coin.indicators.RsiSettingViewModel
import cash.p.terminal.modules.coin.investments.CoinInvestmentsService
import cash.p.terminal.modules.coin.investments.CoinInvestmentsViewModel
import cash.p.terminal.modules.coin.majorholders.CoinMajorHoldersViewModel
import cash.p.terminal.modules.coin.overview.CoinOverviewChartService
import cash.p.terminal.modules.coin.overview.CoinOverviewService
import cash.p.terminal.modules.coin.overview.CoinOverviewViewModel
import cash.p.terminal.modules.coin.ranks.CoinRankViewModel
import cash.p.terminal.modules.coin.reports.CoinReportsService
import cash.p.terminal.modules.coin.reports.CoinReportsViewModel
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesService
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesViewModel
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.modules.manageaccount.backupconfirmkey.BackupConfirmKeyViewModel
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhrasePage
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseViewModel
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesModule
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesViewModel
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsViewModel
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.modules.market.TopMarket
import cash.p.terminal.modules.market.category.CoinCategoryMarketDataChartService
import cash.p.terminal.modules.market.category.MarketCategoryService
import cash.p.terminal.modules.market.category.MarketCategoryViewModel
import cash.p.terminal.modules.market.filters.MarketFiltersService
import cash.p.terminal.modules.market.filters.MarketFiltersViewModel
import cash.p.terminal.modules.market.filtersresult.MarketFiltersResultService
import cash.p.terminal.modules.market.filtersresult.MarketFiltersResultViewModel
import cash.p.terminal.modules.market.metricspage.MetricsPageChartService
import cash.p.terminal.modules.market.metricspage.MetricsPageViewModel
import cash.p.terminal.modules.market.platform.MarketPlatformCoinsRepository
import cash.p.terminal.modules.market.platform.MarketPlatformViewModel
import cash.p.terminal.modules.market.platform.PlatformChartService
import cash.p.terminal.modules.market.topcoins.MarketTopCoinsViewModel
import cash.p.terminal.modules.market.topnftcollections.TopNftCollectionsService
import cash.p.terminal.modules.market.topnftcollections.TopNftCollectionsViewModel
import cash.p.terminal.modules.market.topplatforms.Platform
import cash.p.terminal.modules.market.tvl.TvlChartService
import cash.p.terminal.modules.market.tvl.TvlChartViewModel
import cash.p.terminal.modules.market.tvl.TvlService
import cash.p.terminal.modules.market.tvl.TvlViewItemFactory
import cash.p.terminal.modules.market.tvl.TvlViewModel
import cash.p.terminal.modules.metricchart.MetricsType
import cash.p.terminal.modules.metricchart.ProChartModule
import cash.p.terminal.modules.metricchart.ProChartService
import cash.p.terminal.modules.restorelocal.RestoreLocalViewModel
import cash.p.terminal.modules.watchaddress.WatchAddressViewModel
import cash.p.terminal.modules.watchaddress.selectblockchains.SelectBlockchainsViewModel
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.network.github.data.GithubApiConfig
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IDeletedWalletRestorer
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.di.marketModule
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.modules.balance.BalancePlatformActions
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.core.managers.CoinManager
import cash.p.terminal.wallet.providers.CryptoCompareProvider
import io.horizontalsystems.core.CurrencyManager
import cash.p.terminal.core.managers.DeletedAccountsCleanup
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.core.managers.EvmHardwareSigners
import cash.p.terminal.core.managers.EvmSignerFactory
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.network.pirate.domain.useCase.FiatCurrencyRateService
import cash.p.terminal.modules.market.tvl.GlobalMarketRepository
import cash.p.terminal.modules.managewallets.HardwareWalletKeyProvisioner
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.core.IBackupManager
import cash.p.terminal.manager.IConnectivityManager
import io.horizontalsystems.core.IEncryptionManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import io.horizontalsystems.core.ILanguageManager
import cash.p.terminal.modules.transactions.ITransactionRecordRepository
import cash.p.terminal.shared.main.MainTabStorage
import cash.p.terminal.modules.market.MarketPages
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import io.horizontalsystems.core.PriceChangeIntervalProvider
import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.wallet.providers.RetrofitUtils
import cash.p.terminal.shared.settings.SettingsPages
import cash.p.terminal.modules.blockchainstatus.StatusReportExporter
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.usecase.SyncPendingMultiSwapUseCase
import cash.p.terminal.modules.coin.overview.TokenWalletActions
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.network.alphaaml.api.AlphaAmlApi
import io.horizontalsystems.chartview.chart.AbstractChartService
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import cash.p.terminal.modules.backupalert.BackupAlertViewModel
import cash.p.terminal.modules.basecurrency.BaseCurrencySettingsViewModel
import cash.p.terminal.modules.createaccount.CreateAdvancedAccountViewModel
import cash.p.terminal.modules.importwallet.ImportWalletViewModel
import cash.p.terminal.modules.intro.IntroViewModel
import cash.p.terminal.modules.manageaccount.ManageAccountViewModel
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyViewModel
import cash.p.terminal.modules.manageaccount.privatekeys.PrivateKeysViewModel
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysViewModel
import cash.p.terminal.modules.manageaccount.zcashkeys.ZcashKeysViewModel
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureViewModel
import cash.p.terminal.modules.mwebconfigure.MwebConfigureViewModel
import cash.p.terminal.modules.premium.about.AboutPremiumViewModel
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestoreMnemonicNonStandardViewModel
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKeyViewModel
import cash.p.terminal.modules.settings.terms.TermsViewModel
import cash.p.terminal.modules.unlinkaccount.UnlinkAccountViewModel
import cash.p.terminal.modules.zcashconfigure.ZcashConfigureViewModel
import cash.p.terminal.shared.desktop.lock.ChangePasswordViewModel
import cash.p.terminal.shared.desktop.lock.ConfirmPasswordViewModel
import cash.p.terminal.shared.desktop.lock.CreatePasswordViewModel
import cash.p.terminal.shared.desktop.lock.ForgotPasswordViewModel
import cash.p.terminal.shared.desktop.lock.UnlockViewModel
import cash.p.terminal.shared.desktop.session.mnemonicAccount
import cash.p.terminal.shared.desktop.settings.DesktopAutoLockIntervalsViewModel
import cash.p.terminal.shared.desktop.settings.DesktopLanguageSettingsViewModel
import cash.p.terminal.shared.desktop.qrscanner.DesktopQrScannerViewModel
import cash.p.terminal.modules.qrscanner.QRScannerViewModel
import cash.p.terminal.shared.desktop.settings.DesktopSecurityViewModel
import com.google.gson.Gson
import kotlin.io.path.createTempDirectory
import kotlin.reflect.KClass
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.parameter.parametersOf

class DesktopKoinGraphTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun verifyDesktopKoinGraph() {
        val desktopGraph = module {
            includes(networkModule, marketModule, marketFeatureModule, desktopFeatureModule)
        }

        desktopGraph.verify(
            extraTypes = providedOutsideTheGraph + builtFromInjectedParameters + stakingPageParameters +
                notReachedOnDesktop + absentOnDesktop,
            injections = injectedParameters(
                definition<PassphraseTermsViewModel>(Array<String>::class),
                definition<SafetyRulesViewModel>(SafetyRulesModule.SafetyRulesMode::class, List::class),
                definition<ManageAccountsViewModel>(ManageAccountsModule.Mode::class),
                definition<WatchAddressViewModel>(AddressParserChain::class),
                definition<SelectBlockchainsViewModel>(AccountType::class),
                definition<BackupConfirmKeyViewModel>(IRandomProvider::class),
                definition<RecoveryPhraseViewModel>(RecoveryPhrasePage.RecoveryPhraseType::class),
                definition<SelectBackupItemsViewModel>(BackupViewItemFactory::class),
                definition<BackupLocalPasswordViewModel>(BackupType::class, PassphraseValidator::class),
                definition<RestoreLocalViewModel>(String::class, BackupViewItemFactory::class),
                definition<EvmNetworkViewModel>(Blockchain::class),
                definition<AddRpcViewModel>(Blockchain::class),
                *marketInjections(),
                *coinPageInjections(),
                *walletPageInjections(),
            ),
        )
    }

    /** `verify` sees constructors only; these are fetched by composables and `getKoinInstance` on desktop screens. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun get_typesTheWalletScreensLookUp_resolveOnDesktop() {
        // As desktop main(): key-deriving view models need the kits' crypto provider.
        EthereumKit.init()
        // Only construction is checked: the view models' main-thread work never runs against the locked test storage.
        Dispatchers.setMain(StandardTestDispatcher())
        DesktopTestGraph.start()
        try {
            val koin = GlobalContext.get()
            lookedUpByWalletScreens.forEach { type -> koin.get<Any>(type) }
            viewModelsOfDesktopScreens().forEach { (type, parameters) -> koin.get<Any>(type, parameters = parameters) }
            lookedUpAfterTheFirstUnlock.forEach { type -> assertStopsOnlyAtTheLockedStorage(type) { koin.get<Any>(type) } }
            viewModelsBuiltAfterTheFirstUnlock().forEach { (type, parameters) ->
                assertStopsOnlyAtTheLockedStorage(type) { koin.get<Any>(type, parameters = parameters) }
            }
            assertStopsOnlyAtTheLockedStorage(ManageWalletsViewModel::class) {
                ManageWalletsModule.Factory().create(ManageWalletsViewModel::class, CreationExtras.Empty)
            }
        } finally {
            DesktopTestGraph.stop()
            Dispatchers.resetMain()
        }
    }

    /** Locked, these stop at the storage before reaching every dependency; open, each must build in full. */
    @Test
    fun get_typesLookedUpAfterTheFirstUnlock_buildOverTheOpenStorage() {
        EthereumKit.init()
        DesktopTestGraph.startUnlocked()
        try {
            val koin = GlobalContext.get()
            lookedUpAfterTheFirstUnlock.forEach { type -> koin.get<Any>(type) }
            assertIs<BeamAccountDeletionPreflight>(koin.get<AccountDeletionPreflight>())
            assertIs<DesktopBeamKitDatabaseKeyProvider>(koin.get<BeamKitDatabaseKeyProvider>())
        } finally {
            DesktopTestGraph.stop()
        }
    }

    /** The storage opens at the first unlock: until then these may stop at it, and at nothing else. */
    private fun assertStopsOnlyAtTheLockedStorage(type: KClass<*>, resolve: () -> Unit) {
        try {
            resolve()
        } catch (e: InstanceCreationException) {
            val causes = generateSequence<Throwable>(e) { it.cause }
            assertTrue(causes.any { it is StorageLockedException }, "$type: ${causes.last()}")
        }
    }

    /** Pages behind the unlock gate, built over storage-backed services. */
    private fun viewModelsBuiltAfterTheFirstUnlock(): List<Pair<KClass<*>, ParametersDefinition?>> {
        val wallet = mockk<Wallet>(relaxed = true)
        return listOf(
            AboutPremiumViewModel::class to null,
            ManageAccountsViewModel::class to { parametersOf(ManageAccountsModule.Mode.Manage) },
            BalanceViewModel::class to null,
            BalanceAccountsViewModel::class to null,
            TransactionsViewModel::class to null,
            OfflineSignedTransactionsViewModel::class to null,
            TokenBalanceViewModel::class to { parametersOf(wallet) },
            SyncErrorViewModel::class to { parametersOf(wallet) },
            ContactsViewModel::class to { parametersOf(Mode.Full) },
            AddressCheckerViewModel::class to null,
            AddressCheckBlockchainSelectorViewModel::class to null,
            UnifiedAddressCheckerViewModel::class to { parametersOf("address") },
            // Not BlockchainStatusViewModel: its init reads the locked log DB on IO; verify() covers it.
        )
    }

    private fun marketInjections() = arrayOf(
        definition<MarketTopCoinsViewModel>(TopMarket::class, SortingField::class),
        definition<MarketFiltersViewModel>(MarketFiltersService::class),
        definition<MarketFiltersResultViewModel>(MarketFiltersResultService::class),
        definition<MarketCategoryViewModel>(MarketCategoryService::class),
        definition<MarketPlatformViewModel>(Platform::class, MarketPlatformCoinsRepository::class),
        definition<ChartViewModel>(AbstractChartService::class, ChartModule.ChartNumberFormatter::class),
        definition<CoinCategoryMarketDataChartService>(String::class),
        definition<PlatformChartService>(Platform::class),
        definition<TvlViewModel>(TvlService::class, TvlViewItemFactory::class),
        definition<TvlChartViewModel>(TvlChartService::class, ChartCurrencyValueFormatterShortened::class),
        definition<TopNftCollectionsViewModel>(TopNftCollectionsService::class),
        definition<MetricsPageViewModel>(MetricsType::class),
        definition<MetricsPageChartService>(MetricsType::class),
    )

    private fun coinPageInjections() = arrayOf(
        definition<CoinViewModel>(FullCoin::class, CoinService::class, List::class),
        definition<CoinOverviewViewModel>(FullCoin::class, CoinOverviewService::class, CoinViewFactory::class),
        definition<CoinOverviewChartService>(String::class),
        definition<ProChartService>(String::class, ProChartModule.ChartType::class),
        definition<MovingAverageSettingViewModel>(ChartIndicatorSetting::class),
        definition<MacdSettingViewModel>(ChartIndicatorSetting::class),
        definition<RsiSettingViewModel>(ChartIndicatorSetting::class),
        definition<CoinAnalyticsViewModel>(CoinAnalyticsService::class, String::class),
        definition<CoinAuditsViewModel>(List::class),
        definition<CoinMarketsViewModel>(CoinMarketsService::class),
        definition<CoinInvestmentsViewModel>(CoinInvestmentsService::class),
        definition<CoinMajorHoldersViewModel>(String::class, Blockchain::class, CoinViewFactory::class),
        definition<CoinReportsViewModel>(CoinReportsService::class),
        definition<CoinTreasuriesViewModel>(CoinTreasuriesService::class),
        definition<DetectorsViewModel>(String::class, List::class),
        definition<CoinRankViewModel>(RankType::class, Currency::class),
    )

    private fun walletPageInjections() = arrayOf(
        definition<OfflineModeToggleViewModel>(Wallet::class),
        definition<TokenBalanceViewModel>(Wallet::class),
        definition<CreationBlockViewModel>(Wallet::class),
        definition<AddressPoisoningViewModel>(String::class, Boolean::class, BlockchainType::class),
        definition<ReceiveAddressViewModel>(Wallet::class),
        definition<ReceiveMoneroViewModel>(Wallet::class),
        definition<ReceiveStellarAssetViewModel>(Wallet::class),
        definition<ActivateTokenViewModel>(Wallet::class, Token::class),
        definition<NetworkSelectViewModel>(Account::class, FullCoin::class),
        definition<ReceiveTokenSelectViewModel>(Account::class),
        definition<BchAddressTypeSelectViewModel>(String::class),
        definition<DerivationSelectViewModel>(String::class),
        definition<SelectContactViewModel>(Contact::class, BlockchainType::class),
        definition<ConfiguredTokenInfoViewModel>(Token::class),
        definition<BlockchainStatusViewModel>(BlockchainStatusProvider::class),
        definition<ContactsViewModel>(Mode::class),
        definition<ContactViewModel>(Contact::class, ContactAddress::class),
        definition<AddressViewModel>(String::class, ContactAddress::class, List::class),
        definition<ChooseContactViewModel>(BlockchainType::class),
        definition<AddressCheckTokenSelectorViewModel>(Blockchain::class),
        definition<UnifiedAddressCheckerViewModel>(String::class),
    )

    /** What desktop pages ask `koinViewModel` for, with representative parameters. */
    private fun viewModelsOfDesktopScreens(): List<Pair<KClass<*>, ParametersDefinition?>> {
        val account = mnemonicAccount(isBackedUp = true)
        // RestoreLocalViewModel deletes the file it reads: give it a path that does not exist.
        val backupPath = File(createTempDirectory().toFile(), "backup.json").path
        return listOf(
            IntroViewModel::class to null,
            TermsViewModel::class to null,
            ImportWalletViewModel::class to null,
            CreateAdvancedAccountViewModel::class to null,
            RestoreMnemonicViewModel::class to null,
            RestoreMnemonicNonStandardViewModel::class to null,
            RestorePrivateKeyViewModel::class to null,
            MoneroConfigureViewModel::class to null,
            MwebConfigureViewModel::class to null,
            ZcashConfigureViewModel::class to null,
            WatchAddressViewModel::class to null,
            BackupAlertViewModel::class to null,
            // Not SelectBackupItemsViewModel: its init reads the locked vault DB on IO; verify() covers it.
            BaseCurrencySettingsViewModel::class to null,
            AppearanceViewModel::class to null,
            SelectBlockchainsViewModel::class to { parametersOf(account.type, account.name) },
            DuplicateWalletViewModel::class to { parametersOf(account) },
            ManageAccountViewModel::class to { parametersOf(account) },
            UnlinkAccountViewModel::class to { parametersOf(account) },
            PrivateKeysViewModel::class to { parametersOf(account) },
            PublicKeysViewModel::class to { parametersOf(account) },
            BackupConfirmKeyViewModel::class to { parametersOf(account) },
            RecoveryPhraseViewModel::class to {
                parametersOf(account, RecoveryPhrasePage.RecoveryPhraseType.Mnemonic)
            },
            BackupKeyViewModel::class to { parametersOf(account.id) },
            ZcashKeysViewModel::class to { parametersOf(account.id) },
            BackupLocalPasswordViewModel::class to { parametersOf(BackupType.SingleWalletBackup(account.id)) },
            RestoreLocalViewModel::class to { parametersOf(backupPath, "backup.json") },
            PassphraseTermsViewModel::class to { parametersOf(arrayOf("term")) },
            SafetyRulesViewModel::class to {
                parametersOf(SafetyRulesModule.SafetyRulesMode.AGREE, listOf("rule"))
            },
            CreatePasswordViewModel::class to null,
            UnlockViewModel::class to null,
            ForgotPasswordViewModel::class to null,
            ChangePasswordViewModel::class to null,
            ConfirmPasswordViewModel::class to null,
            DesktopSecurityViewModel::class to null,
            DesktopAutoLockIntervalsViewModel::class to null,
            DesktopLanguageSettingsViewModel::class to null,
            QRScannerViewModel::class to null,
            DesktopQrScannerViewModel::class to null,
        )
    }

    private companion object {
        val lookedUpByWalletScreens = listOf(
            WalletPlatformPages::class,
            WalletSendPages::class,
            IBalanceHiddenManager::class,
            ContactsRepository::class,
            IDeletedWalletRestorer::class,
            SecretActionAuthorizer::class,
            AppRestarter::class,
            DesktopAppRestarter::class,
            AppPages::class,
            WalletPages::class,
            BackupFileLauncher::class,
            WalletOnboardingStorage::class,
            LocalizedAssetReader::class,
            UrlOpener::class,
            IClipboardManager::class,
            DispatcherProvider::class,
            AddressResolutionConfig::class,
            GetHardwarePublicKeyForWalletUseCase::class,
            IAccountFactory::class,
            IAccountManager::class,
            ITermsManager::class,
            IWalletManager::class,
            MarketKitWrapper::class,
            LitecoinBirthdayProvider::class,
            MoneroWalletUseCase::class,
            RestoreSettingsManager::class,
            TokenAutoEnableManager::class,
            WalletFactory::class,
            ZcashAddressDeriver::class,
            ZcashBirthdayProvider::class,
            ZcashKeyExporter::class,
            ZcashDbMarker::class,
            MoneroHardwareGateway::class,
            AddressLabelManager::class,
            BalancePlatformActions::class,
            ChartIndicatorManager::class,
            CoinManager::class,
            CryptoCompareProvider::class,
            CurrencyManager::class,
            DesktopHudMessenger::class,
            EvmHardwareSigners::class,
            EvmSignerFactory::class,
            FiatCurrencyRateService::class,
            GlobalMarketRepository::class,
            HardwareWalletKeyProvisioner::class,
            HardwareWalletTokenPolicy::class,
            IBackupManager::class,
            IConnectivityManager::class,
            IEncryptionManager::class,
            IHardwarePublicKeyStorage::class,
            ILanguageManager::class,
            MainTabStorage::class,
            MarketPages::class,
            PendingTransactionMatcher::class,
            PendingTransactionRegistrar::class,
            PiratePlaceRepository::class,
            PriceChangeIntervalProvider::class,
            PriceManager::class,
            RetrofitUtils::class,
            SettingsPages::class,
            StatusReportExporter::class,
            SwapProviderTransactionsStorage::class,
            TokenWalletActions::class,
            UpdateSwapProviderTransactionsStatusUseCase::class,
            AlphaAmlApi::class,
            // Built at startup, before any unlock.
            DesktopWalletSession::class,
            DesktopWipeCoordinator::class,
            DesktopAppExit::class,
            DesktopAppRelauncher::class,
        )

        val lookedUpAfterTheFirstUnlock = listOf(
            CheckPremiumUseCase::class,
            IAdapterManager::class,
            TransactionAdapterManager::class,
            WalletServicesStarter::class,
            WalletUseCase::class,
            RescanMoneroUseCase::class,
            UserDeletedWalletManager::class,
            OfflineModeManager::class,
            OfflineModeUseCase::class,
            EvmBlockchainManager::class,
            BtcBlockchainManager::class,
            PlatformChainSupport::class,
            PlatformOperationSupport::class,
            PoisonAddressManager::class,
            AmlStatusManager::class,
            NetworkErrorTracker::class,
            PendingMultiSwapStorage::class,
            SeedPhraseQrCrypto::class,
            GetRestoreHeightForWalletUseCase::class,
            DeleteContactsPinSettings::class,
            BalanceHideOnFlipHandler::class,
            AppearanceSettingsStore::class,
            ICoinManager::class,
            IAppNumberFormatter::class,
            TronKitManager::class,
            TonKitManager::class,
            StellarKitManager::class,
            SolanaKitManager::class,
            MoneroKitManager::class,
            ThorchainKitManagers::class,
            DeletedAccountsCleanup::class,
            EvmTransactionRepository::class,
            IAccountCleaner::class,
            ITransactionRecordRepository::class,
            OfflineOperationGate::class,
            SyncPendingMultiSwapUseCase::class,
            BeamSessionOwner::class,
            BeamLifecycleCoordinator::class,
            BeamSendCoordinator::class,
            BeamKitDatabaseKeyProvider::class,
            AccountDeletionPreflight::class,
        )

        /** Headers and the premium URL come from `desktopApp`; files, flags and the engine are passed by the definitions. */
        val providedOutsideTheGraph = listOf(
            AppHeadersProvider::class,
            File::class,
            Gson::class,
            Boolean::class,
            String::class,
            HttpClientEngine::class,
        )

        /** Built inside the view model definitions from their injected parameters. */
        val builtFromInjectedParameters = listOf(
            BtcBlockchainSettingsService::class,
            SyncErrorService::class,
            AddressHandlerFactory::class,
            Eip20AddressValidator::class,
            TransactionInfoService::class,
            TransactionInfoViewItemFactory::class,
            FullCoinsProvider::class,
            XRateService::class,
            DefaultBalanceService::class,
            TotalBalance::class,
            TokenBalanceService::class,
            TokenTransactionsService::class,
            EnabledWalletsCacheDao::class,
            // Built inside the EnsResolver definition.
            RpcSource::class,
        )

        /** The staking view model is internal; its optional initial tab comes as a parameter. */
        val stakingPageParameters = listOf(StackingType::class)

        /** GitHub release checks have no desktop screen that resolves them. */
        val notReachedOnDesktop = listOf(GithubApiConfig::class)

        /** Resolved with `getOrNull`: desktop has no such feature and hides what needs it. */
        val absentOnDesktop = listOf(PremiumPlatformSettings::class)
    }
}
