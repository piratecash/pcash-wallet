package cash.p.terminal.feature.market.di

import cash.p.terminal.modules.basecurrency.BaseCurrencySettingsViewModel
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.coin.CoinService
import cash.p.terminal.modules.coin.CoinViewFactory
import cash.p.terminal.modules.coin.CoinViewModel
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsService
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsViewModel
import cash.p.terminal.modules.coin.analytics.TechnicalAdviceViewItemFactory
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
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesRepository
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesService
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesViewModel
import cash.p.terminal.modules.coin.indicators.ChartIndicatorsViewModel
import cash.p.terminal.modules.coin.indicators.MacdSettingViewModel
import cash.p.terminal.modules.coin.indicators.MovingAverageSettingViewModel
import cash.p.terminal.modules.coin.indicators.RsiSettingViewModel
import cash.p.terminal.modules.coin.overview.CoinOverviewChartService
import cash.p.terminal.modules.coin.overview.CoinOverviewService
import cash.p.terminal.modules.coin.overview.MarketFeatureConfig
import cash.p.terminal.modules.coin.overview.CoinOverviewViewModel
import cash.p.terminal.modules.chart.ChartCurrencyValueFormatterShortened
import cash.p.terminal.modules.market.MarketViewModel
import cash.p.terminal.modules.market.category.CoinCategoryMarketDataChartService
import cash.p.terminal.modules.market.category.MarketCategoryRepository
import cash.p.terminal.modules.market.category.MarketCategoryService
import cash.p.terminal.modules.market.category.MarketCategoryViewModel
import cash.p.terminal.modules.market.favorites.MarketFavoritesMenuService
import cash.p.terminal.modules.market.favorites.MarketFavoritesRepository
import cash.p.terminal.modules.market.favorites.MarketFavoritesService
import cash.p.terminal.modules.market.favorites.MarketFavoritesViewModel
import cash.p.terminal.modules.market.filters.MarketFiltersService
import cash.p.terminal.modules.market.filters.MarketFiltersViewModel
import cash.p.terminal.modules.market.filtersresult.MarketFiltersResultService
import cash.p.terminal.modules.market.filtersresult.MarketFiltersResultViewModel
import cash.p.terminal.modules.market.overview.MarketOverviewService
import cash.p.terminal.modules.market.overview.MarketOverviewViewModel
import cash.p.terminal.modules.market.posts.MarketPostService
import cash.p.terminal.modules.market.platform.MarketPlatformCoinsRepository
import cash.p.terminal.modules.market.platform.MarketPlatformViewModel
import cash.p.terminal.modules.market.platform.PlatformChartService
import cash.p.terminal.modules.market.posts.MarketPostsViewModel
import cash.p.terminal.modules.market.search.MarketDiscoveryService
import cash.p.terminal.modules.market.search.MarketSearchService
import cash.p.terminal.modules.market.search.MarketSearchViewModel
import cash.p.terminal.modules.market.topcoins.MarketTopCoinsViewModel
import cash.p.terminal.modules.market.topcoins.MarketTopMoversRepository
import cash.p.terminal.modules.market.toppairs.TopPairsViewModel
import cash.p.terminal.modules.market.etf.EtfViewModel
import cash.p.terminal.modules.market.metricspage.MetricsPageChartService
import cash.p.terminal.modules.market.metricspage.MetricsPageViewModel
import cash.p.terminal.modules.market.tvl.GlobalMarketRepository
import cash.p.terminal.modules.market.tvl.TvlChartService
import cash.p.terminal.modules.market.tvl.TvlChartViewModel
import cash.p.terminal.modules.market.tvl.TvlService
import cash.p.terminal.modules.market.tvl.TvlViewItemFactory
import cash.p.terminal.modules.market.tvl.TvlViewModel
import cash.p.terminal.modules.market.topplatforms.Platform
import cash.p.terminal.modules.market.topplatforms.TopPlatformsRepository
import cash.p.terminal.modules.market.topplatforms.TopPlatformsViewModel
import cash.p.terminal.modules.metricchart.ProChartService
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.models.CoinCategory
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val marketFeatureModule = module {
    viewModelOf(::MarketViewModel)
    viewModelOf(::MarketFavoritesViewModel)
    viewModel { params -> MarketTopCoinsViewModel(params.get(), params.get(), get(), get(), get(), get()) }
    viewModelOf(::MarketPostsViewModel)
    viewModelOf(::TopPlatformsViewModel)
    viewModelOf(::TopPairsViewModel)
    viewModelOf(::MarketOverviewViewModel)
    viewModelOf(::BaseCurrencySettingsViewModel)
    viewModelOf(::MarketSearchViewModel)
    viewModel { MarketFiltersViewModel(MarketFiltersService(get(), get<CurrencyManager>().baseCurrency), get()) }
    viewModel { params -> MarketFiltersResultViewModel(MarketFiltersResultService(params.get(), get()), get()) }
    viewModel { params ->
        val category = params.get<CoinCategory>()
        val service = MarketCategoryService(MarketCategoryRepository(get()), get(), get(), get(), category, get())
        MarketCategoryViewModel(service, get())
    }
    viewModel { params ->
        val platform = params.get<Platform>()
        MarketPlatformViewModel(platform, MarketPlatformCoinsRepository(platform, get(), get()), get(), get(), get())
    }
    viewModel { params -> ChartViewModel(params.get(), params.get(), get(), get()) }
    viewModel { params ->
        val service = CoinService(params.get<FullCoin>(), get(), get())
        CoinViewModel(service, listOf(service), get(), get())
    }
    viewModel { params ->
        val service = CoinOverviewService(
            params.get<FullCoin>(), get(), get(), get(), get<MarketFeatureConfig>().guidesUrl, get()
        )
        val factory = CoinViewFactory(get<CurrencyManager>().baseCurrency, get())
        CoinOverviewViewModel(service, factory, get(), get(), get(), get())
    }
    viewModel { params ->
        val fullCoin = params.get<FullCoin>()
        val service = CoinAnalyticsService(fullCoin, get(), get(), get(), get())
        CoinAnalyticsViewModel(service, get(), get(), fullCoin.coin.code, get())
    }
    viewModel { params -> CoinAuditsViewModel(params.get(), get()) }
    viewModel { params -> CoinMarketsViewModel(CoinMarketsService(params.get(), get(), get()), get()) }
    viewModel { params -> CoinInvestmentsViewModel(CoinInvestmentsService(params.get(), get(), get()), get()) }
    viewModel { params ->
        val factory = CoinViewFactory(get<CurrencyManager>().baseCurrency, get())
        CoinMajorHoldersViewModel(params.get(), params.get(), get(), factory, get())
    }
    viewModel { params -> CoinReportsViewModel(CoinReportsService(params.get(), get())) }
    viewModel { params ->
        val service = CoinTreasuriesService(params.get(), CoinTreasuriesRepository(get()), get())
        CoinTreasuriesViewModel(service, get())
    }
    viewModel { params -> DetectorsViewModel(params.get(), params.get(), get()) }
    viewModel { params -> CoinRankViewModel(params.get(), get<CurrencyManager>().baseCurrency, get(), get(), get()) }
    viewModelOf(::EtfViewModel)
    viewModel { params -> TvlViewModel(TvlService(get(), params.get<GlobalMarketRepository>()), TvlViewItemFactory()) }
    viewModel { params ->
        TvlChartViewModel(
            TvlChartService(get(), params.get<GlobalMarketRepository>()),
            ChartCurrencyValueFormatterShortened(get()), get(), get()
        )
    }
    viewModel { params -> MetricsPageViewModel(params.get(), get(), get()) }
    viewModelOf(::ChartIndicatorsViewModel)
    viewModel { params -> MovingAverageSettingViewModel(params.get(), get()) }
    viewModel { params -> MacdSettingViewModel(params.get(), get()) }
    viewModel { params -> RsiSettingViewModel(params.get(), get()) }

    singleOf(::ChartIndicatorManager)
    singleOf(::MarketFavoritesRepository)
    singleOf(::MarketFavoritesMenuService)

    factoryOf(::TechnicalAdviceViewItemFactory)
    factoryOf(::MarketFavoritesService)
    factoryOf(::MarketPostService)
    factoryOf(::MarketOverviewService)
    factoryOf(::MarketTopMoversRepository)
    factoryOf(::TopPlatformsRepository)
    factoryOf(::GlobalMarketRepository)
    factoryOf(::MarketSearchService)
    factoryOf(::MarketDiscoveryService)
    factory { params -> CoinCategoryMarketDataChartService(get(), get(), params.get()) }
    factory { params -> MetricsPageChartService(get(), params.get(), get()) }
    factory { params -> PlatformChartService(params.get(), get(), get()) }
    factory { params -> CoinOverviewChartService(get(), get(), params.get(), get()) }
    factory { params -> ProChartService(get(), get(), params.get(), params.get()) }
}
