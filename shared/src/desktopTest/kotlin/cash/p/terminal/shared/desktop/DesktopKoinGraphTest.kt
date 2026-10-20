package cash.p.terminal.shared.desktop

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
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.feature.wallet.LocalizedAssetReader
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.navigation.WalletPages
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.mockk
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
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
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IDeletedWalletRestorer
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.di.marketModule
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import io.horizontalsystems.chartview.chart.AbstractChartService
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.ethereumkit.core.EthereumKit
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
            extraTypes = providedOutsideTheGraph + builtFromInjectedParameters + notReachedOnDesktop,
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
            loadKoinModules(listOf(networkModule, networkStandIns))
            val koin = GlobalContext.get()
            lookedUpByWalletScreens.forEach { type -> koin.get<Any>(type) }
            viewModelsOfDesktopScreens().forEach { (type, parameters) -> koin.get<Any>(type, parameters = parameters) }
        } finally {
            DesktopTestGraph.stop()
            Dispatchers.resetMain()
        }
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
            AboutPremiumViewModel::class to null,
            BaseCurrencySettingsViewModel::class to null,
            AppearanceViewModel::class to null,
            ManageAccountsViewModel::class to { parametersOf(ManageAccountsModule.Mode.Manage) },
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
        )
    }

    private companion object {
        val lookedUpByWalletScreens = listOf(
            WalletPlatformPages::class,
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
            CheckPremiumUseCase::class,
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
        )

        /** Over the real network module, so nothing here calls out. */
        val networkStandIns = module {
            single<BinanceApi> { mockk(relaxed = true) }
            single<PiratePlaceRepository> { mockk(relaxed = true) }
            single<GetZcashHeightUseCase> { mockk(relaxed = true) }
        }

        /** Headers and the premium URL come from `desktopApp`; files, flags and the engine are passed by the definitions. */
        val providedOutsideTheGraph = listOf(
            AppHeadersProvider::class,
            File::class,
            Gson::class,
            Boolean::class,
            String::class,
            HttpClientEngine::class,
        )

        /** Built inside the BtcBlockchainSettingsViewModel definition from its injected Blockchain. */
        val builtFromInjectedParameters = listOf(BtcBlockchainSettingsService::class)

        /** GitHub release checks and the scan-to-add flow have no desktop screen that resolves them. */
        val notReachedOnDesktop = listOf(
            GithubApiConfig::class,
            IDeletedWalletRestorer::class,
            ScanToAddUseCase::class,
        )
    }
}
