package cash.p.terminal.core.di

import android.app.Application
import android.content.Context
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppDeeplinkInput
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppViewModel
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusViewModel
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewModel
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsService
import cash.p.terminal.modules.configuredtoken.ConfiguredTokenInfoViewModel
import cash.p.terminal.modules.evmnetwork.EvmNetworkViewModel
import cash.p.terminal.modules.evmnetwork.addrpc.AddRpcViewModel
import cash.p.terminal.modules.multiswap.SwapSelectCoinViewModel
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesModule
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesViewModel
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsViewModel
import cash.p.terminal.modules.address.AddressParserChain
import cash.p.terminal.modules.watchaddress.WatchAddressViewModel
import cash.p.terminal.modules.watchaddress.selectblockchains.SelectBlockchainsViewModel
import cash.p.terminal.modules.manageaccount.backupconfirmkey.BackupConfirmKeyViewModel
import cash.p.terminal.modules.backuplocal.fullbackup.BackupViewItemFactory
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel
import cash.p.terminal.modules.backuplocal.password.BackupLocalPasswordViewModel
import cash.p.terminal.modules.backuplocal.password.BackupType
import cash.p.terminal.modules.restorelocal.RestoreLocalViewModel
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.core.IRandomProvider
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhrasePage
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseViewModel
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.modules.multiswap.TimerService
import cash.p.terminal.modules.pin.hiddenwallet.HiddenWalletPinPolicy
import cash.p.terminal.modules.paycore.PayCoreTicker
import cash.p.terminal.modules.paycore.exchange.PayCoreExchangeDetailViewModel
import cash.p.terminal.modules.paycore.payment.PayCorePaymentParams
import cash.p.terminal.modules.paycore.payment.PayCorePaymentViewModel
import cash.p.terminal.modules.paycore.verification.PayCoreVerificationViewModel
import cash.p.terminal.modules.settings.advancedsecurity.AdvancedSecurityViewModel
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.terms.DeleteContactsTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.modules.softwareupdate.changelog.VersionChangelogViewModel
import cash.p.terminal.modules.softwareupdate.domain.ChangelogRequest
import cash.p.terminal.core.notifications.polling.TransactionPollingManager
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedSheet
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedViewModel
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.IPinComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.mockk.mockk
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.annotation.Provided
import org.koin.core.definition.BeanDefinition
import org.koin.core.module.Module
import org.koin.core.module.flatten
import org.koin.dsl.module
import org.koin.ext.getFullName
import org.koin.test.KoinTest
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.modules.market.TopMarket
import cash.p.terminal.modules.market.category.CoinCategoryMarketDataChartService
import cash.p.terminal.modules.market.category.MarketCategoryService
import cash.p.terminal.modules.market.category.MarketCategoryViewModel
import cash.p.terminal.modules.market.filters.MarketFiltersService
import cash.p.terminal.modules.market.filters.MarketFiltersViewModel
import cash.p.terminal.modules.market.filtersresult.MarketFiltersResultService
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.RankType
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsService
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsViewModel
import cash.p.terminal.modules.coin.audits.CoinAuditsViewModel
import cash.p.terminal.modules.coin.coinmarkets.CoinMarketsService
import cash.p.terminal.modules.coin.coinmarkets.CoinMarketsViewModel
import cash.p.terminal.modules.coin.detectors.DetectorsViewModel
import cash.p.terminal.modules.coin.investments.CoinInvestmentsService
import cash.p.terminal.modules.coin.investments.CoinInvestmentsViewModel
import cash.p.terminal.modules.coin.majorholders.CoinMajorHoldersViewModel
import cash.p.terminal.modules.coin.ranks.CoinRankViewModel
import cash.p.terminal.modules.coin.reports.CoinReportsService
import cash.p.terminal.modules.coin.reports.CoinReportsViewModel
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesService
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesViewModel
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.Currency
import cash.p.terminal.modules.coin.CoinService
import cash.p.terminal.modules.coin.CoinViewFactory
import cash.p.terminal.modules.coin.CoinViewModel
import cash.p.terminal.modules.coin.indicators.MacdSettingViewModel
import cash.p.terminal.modules.coin.indicators.MovingAverageSettingViewModel
import cash.p.terminal.modules.coin.indicators.RsiSettingViewModel
import cash.p.terminal.modules.coin.overview.CoinOverviewChartService
import cash.p.terminal.modules.coin.overview.CoinOverviewService
import cash.p.terminal.modules.coin.overview.CoinOverviewViewModel
import cash.p.terminal.modules.market.filtersresult.MarketFiltersResultViewModel
import cash.p.terminal.modules.market.platform.MarketPlatformCoinsRepository
import cash.p.terminal.modules.market.platform.MarketPlatformViewModel
import cash.p.terminal.modules.market.platform.PlatformChartService
import cash.p.terminal.modules.market.metricspage.MetricsPageChartService
import cash.p.terminal.modules.market.metricspage.MetricsPageViewModel
import cash.p.terminal.modules.market.topcoins.MarketTopCoinsViewModel
import cash.p.terminal.modules.market.topnftcollections.TopNftCollectionsService
import cash.p.terminal.modules.market.topnftcollections.TopNftCollectionsViewModel
import cash.p.terminal.modules.market.tvl.TvlChartService
import cash.p.terminal.modules.market.tvl.TvlChartViewModel
import cash.p.terminal.modules.market.tvl.TvlService
import cash.p.terminal.modules.market.tvl.TvlViewItemFactory
import cash.p.terminal.modules.market.tvl.TvlViewModel
import cash.p.terminal.modules.chart.ChartCurrencyValueFormatterShortened
import cash.p.terminal.modules.metricchart.MetricsType
import cash.p.terminal.modules.market.topplatforms.Platform
import cash.p.terminal.modules.metricchart.ProChartModule
import cash.p.terminal.modules.metricchart.ProChartService
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.chartview.chart.AbstractChartService
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.chartview.chart.ChartViewModel
import org.koin.test.verify.ParameterTypeInjection
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import org.junit.Test
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.primaryConstructor

class KoinGraphTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun verifyKoinGraph() {
        val testOverrides = module {
            single<Context> { mockk(relaxed = true) }
            single<Application> { mockk(relaxed = true) }
            single<HttpClientEngine> { OkHttp.create() }
        }

        val fullModule = module {
            includes(appModule, testOverrides)
        }

        val extraTypes =
            listOf(
                Application::class,
                Context::class,
                File::class,
                HttpClientEngine::class,
                TimerService::class,
                XRateService::class,
                StackingType::class,
                // Built inside the BtcBlockchainSettingsViewModel definition from its injected Blockchain.
                BtcBlockchainSettingsService::class,
            )
        val injections = injectedParameters(
            definition<AccountTypeNotSupportedViewModel>(AccountTypeNotSupportedSheet.Input::class),
            definition<HiddenWalletPinPolicy>(IPinComponent::class),
            definition<AdvancedSecurityViewModel>(IPinComponent::class),
            definition<SecureResetTermsViewModel>(Array<String>::class),
            definition<DeleteContactsTermsViewModel>(Array<String>::class),
            definition<HiddenWalletTermsViewModel>(Array<String>::class),
            definition<PassphraseTermsViewModel>(Array<String>::class),
            definition<ConfiguredTokenInfoViewModel>(Token::class),
            definition<SafetyRulesViewModel>(SafetyRulesModule.SafetyRulesMode::class, List::class),
            definition<ManageAccountsViewModel>(ManageAccountsModule.Mode::class),
            definition<ConnectMiniAppViewModel>(ConnectMiniAppDeeplinkInput::class),
            definition<BlockchainStatusViewModel>(BlockchainStatusProvider::class),
            definition<SwapSelectCoinViewModel>(Token::class, Account::class),
            definition<AddressPoisoningViewModel>(String::class, Boolean::class, BlockchainType::class),
            definition<PayCoreVerificationViewModel>(PayCoreTicker::class, String::class),
            definition<PayCorePaymentViewModel>(PayCorePaymentParams::class),
            definition<PayCoreExchangeDetailViewModel>(Long::class),
            definition<MarketTopCoinsViewModel>(TopMarket::class, SortingField::class),
            definition<MarketFiltersViewModel>(MarketFiltersService::class),
            definition<MarketFiltersResultViewModel>(MarketFiltersResultService::class),
            definition<MarketCategoryViewModel>(MarketCategoryService::class),
            definition<MarketPlatformViewModel>(Platform::class, MarketPlatformCoinsRepository::class),
            definition<ChartViewModel>(AbstractChartService::class, ChartModule.ChartNumberFormatter::class),
            definition<CoinCategoryMarketDataChartService>(String::class),
            definition<PlatformChartService>(Platform::class),
            *marketMetricsInjections(),
            *walletInjections(),
            *coinPageInjections(),
            *coinSubPageInjections(),
            definition<TransactionPollingManager>(List::class),
            definition<VersionChangelogViewModel>(ChangelogRequest::class),
        )

        fullModule.verify(
            extraTypes = extraTypes,
            injections = injections,
        )
        fullModule.verifyOptionalConstructorParametersAreResolvable(extraTypes, injections)
    }

    @OptIn(KoinInternalApi::class)
    @Test
    fun appModule_constructorDependencies_haveNoCycles() {
        val definitions = (flatten(appModule.includedModules.toList()) + appModule)
            .flatMap { it.mappings.values }
            .map { it.beanDefinition }
            .toSet()
        val providers = definitions
            .flatMap { definition ->
                (definition.secondaryTypes + definition.primaryType).map { it to definition.primaryType }
            }
            .groupBy({ it.first }, { it.second })
        val dependencies = definitions.associate { definition ->
            definition.primaryType to definition.primaryType.eagerDependencies().flatMap { providers[it].orEmpty() }
        }

        val cycle = findCycle(dependencies)
        check(cycle == null) {
            "Koin constructor dependency cycle: ${cycle?.joinToString(" -> ") { it.qualifiedName.orEmpty() }}"
        }
    }

    private fun KClass<*>.eagerDependencies(): List<KClass<*>> {
        val constructor = tryOrNull { primaryConstructor ?: constructors.maxByOrNull { it.parameters.size } }
        // Lazy<T> is resolved on first use, so it cannot close a construction cycle.
        return constructor?.parameters.orEmpty()
            .filterNot { (it.type.classifier as? KClass<*>)?.simpleName == "Lazy" }
            .mapNotNull { it.injectedType() }
    }

    private fun <T> findCycle(graph: Map<T, List<T>>): List<T>? {
        val visited = mutableSetOf<T>()
        val path = mutableListOf<T>()
        fun visit(node: T): List<T>? {
            val start = path.indexOf(node)
            if (start >= 0) return path.drop(start) + node
            if (!visited.add(node)) return null
            path += node
            graph[node].orEmpty().forEach { next -> visit(next)?.let { return it } }
            path.removeAt(path.lastIndex)
            return null
        }
        return graph.keys.firstNotNullOfOrNull(::visit)
    }

    private fun walletInjections() = arrayOf(
        definition<WatchAddressViewModel>(AddressParserChain::class),
        definition<SelectBlockchainsViewModel>(AccountType::class),
        definition<BackupConfirmKeyViewModel>(IRandomProvider::class),
        definition<RecoveryPhraseViewModel>(RecoveryPhrasePage.RecoveryPhraseType::class),
        definition<SelectBackupItemsViewModel>(BackupViewItemFactory::class),
        definition<BackupLocalPasswordViewModel>(BackupType::class, PassphraseValidator::class),
        definition<RestoreLocalViewModel>(String::class, BackupViewItemFactory::class),
        definition<EvmNetworkViewModel>(Blockchain::class),
        definition<AddRpcViewModel>(Blockchain::class),
    )

    private fun marketMetricsInjections() = arrayOf(
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
    )

    private fun coinSubPageInjections() = arrayOf(
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

    @OptIn(KoinExperimentalAPI::class, KoinInternalApi::class)
    private fun Module.verifyOptionalConstructorParametersAreResolvable(
        extraTypes: List<KClass<*>>,
        injections: List<ParameterTypeInjection>
    ) {
        val allModules = flatten(includedModules.toList()) + this
        val definitionIndex = allModules.flatMap { it.mappings.keys }.toSet()
        val extraTypeNames = extraTypes.map { it.getFullName() }.toSet()
        val injectionIndex = injections.associate {
            it.targetType.getFullName() to it.injectedTypes.map(KClass<*>::getFullName)
        }

        val missingParameters = allModules
            .flatMap { it.mappings.values }
            .toSet()
            .filter { it.beanDefinition.isConstructorReferenceDefinition() }
            .flatMap { factory ->
                factory.beanDefinition.missingOptionalParameters(
                    definitionIndex = definitionIndex,
                    extraTypeNames = extraTypeNames,
                    injectionIndex = injectionIndex,
                )
            }

        check(missingParameters.isEmpty()) {
            missingParameters.joinToString(
                separator = "\n",
                prefix = "Optional constructor parameters must be resolvable by Koin:\n",
            )
        }
    }

    private fun BeanDefinition<*>.missingOptionalParameters(
        definitionIndex: Set<String>,
        extraTypeNames: Set<String>,
        injectionIndex: Map<String, List<String>>,
    ): List<String> {
        val originTypeName = primaryType.getFullName()
        return primaryType.constructors
            .filter { it.visibility == KVisibility.PUBLIC }
            .flatMap { constructor ->
                constructor.parameters
                    .filter(KParameter::isOptional)
                    .filterNot { parameter ->
                        parameter.isResolvableFor(
                            originTypeName = originTypeName,
                            definitionIndex = definitionIndex,
                            extraTypeNames = extraTypeNames,
                            injectionIndex = injectionIndex,
                        )
                    }
                    .map { parameter ->
                        val typeName = parameter.injectedType()?.qualifiedName ?: parameter.type.toString()
                        "${primaryType.qualifiedName}.${parameter.name}: $typeName"
                    }
            }
    }

    private fun BeanDefinition<*>.isConstructorReferenceDefinition(): Boolean {
        val definitionClassName = definition.javaClass.name
        // Explicit Koin lambdas can choose which constructor parameters to pass; constructor DSL cannot.
        return constructorReferenceMarkers.any(definitionClassName::contains)
    }

    private fun KParameter.isResolvableFor(
        originTypeName: String,
        definitionIndex: Set<String>,
        extraTypeNames: Set<String>,
        injectionIndex: Map<String, List<String>>,
    ): Boolean {
        if (annotations.any { it.annotationClass == InjectedParam::class || it.annotationClass == Provided::class }) {
            return true
        }

        val typeName = injectedType()?.getFullName() ?: return false
        return definitionIndex.any { it.contains(typeName) } ||
            typeName in extraTypeNames ||
            typeName in injectionIndex[originTypeName].orEmpty()
    }

    private fun KParameter.injectedType(): KClass<*>? {
        val classifier = type.classifier as? KClass<*> ?: return null
        if (classifier.simpleName == "Lazy" || classifier.simpleName == "List") {
            return type.arguments.firstOrNull()?.type?.classifier as? KClass<*>
        }
        return classifier
    }

    companion object {
        private val constructorReferenceMarkers = listOf("singleOf", "factoryOf", "viewModelOf")
    }
}
