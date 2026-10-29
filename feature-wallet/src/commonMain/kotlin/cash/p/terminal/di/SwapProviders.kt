package cash.p.terminal.di

import cash.p.terminal.core.usecase.FetchSwapQuotesUseCase
import cash.p.terminal.core.usecase.IterativeExactOutSearch
import cash.p.terminal.entities.UnstoppableProviderTitles
import cash.p.terminal.modules.multiswap.AssetFiatRateService
import cash.p.terminal.modules.multiswap.SwapSelectCoinViewModel
import cash.p.terminal.modules.multiswap.TimerService
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeViewModel
import cash.p.terminal.modules.multiswap.exchanges.MultiSwapExchangesViewModel
import cash.p.terminal.modules.multiswap.providers.AndroidUnstoppableProviderTitles
import cash.p.terminal.modules.multiswap.providersettings.SwapProvidersSettingsViewModel
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.modules.multiswap.MultiSwapOnChainMonitor
import cash.p.terminal.modules.multiswap.MultiSwapRouteResolver
import cash.p.terminal.modules.multiswap.StellarTrustlineActionResolver
import cash.p.terminal.modules.multiswap.SwapQuoteService
import cash.p.terminal.modules.multiswap.providers.ChangeNowProvider
import cash.p.terminal.modules.multiswap.providers.ExolixProvider
import cash.p.terminal.modules.multiswap.providers.MayaProvider
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProviderSupport
import cash.p.terminal.modules.multiswap.providers.QuickexProvider
import cash.p.terminal.modules.multiswap.providers.StonFiProvider
import cash.p.terminal.modules.multiswap.providers.SwapProviderTransactionFactory
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRegistry
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.modules.multiswap.providers.ThorChainProvider
import cash.p.terminal.modules.multiswap.providers.ThorChainSwapStatusRepository
import cash.p.terminal.modules.multiswap.providers.UnstoppableProvidersFactory
import cash.p.terminal.modules.multiswap.providers.YiFiProvider
import cash.p.terminal.modules.multiswap.providers.YiFiTokenResolver
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapAssetResolver
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapProvidersFactory
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapProvidersRepository
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapSigner
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val swapProvidersModule = module {
    factoryOf(::SwapQuoteService)
    singleOf(::SwapProviderTransactionFactory)
    singleOf(::OffChainSwapProviderSupport)

    singleOf(::ChangeNowProvider)
    singleOf(::QuickexProvider)
    singleOf(::ExolixProvider)
    singleOf(::YiFiTokenResolver)
    singleOf(::YiFiProvider)
    singleOf(::StonFiProvider)
    singleOf(::BackendSwapSigner)
    singleOf(::BackendSwapAssetResolver)
    singleOf(::BackendSwapProvidersRepository)
    singleOf(::BackendSwapProvidersFactory)

    singleOf(::UnstoppableProvidersFactory)

    single<SwapProviderTransactionStatusRepository>(named(SwapProvider.THORCHAIN)) {
        ThorChainSwapStatusRepository(ThorChainProvider.thornodeAPI)
    }
    single<SwapProviderTransactionStatusRepository>(named(SwapProvider.MAYA)) {
        ThorChainSwapStatusRepository(MayaProvider.thornodeAPI)
    }

    singleOf(::SwapProvidersRegistry)
    singleOf(::MultiSwapOnChainMonitor)
    singleOf(::MultiSwapRouteResolver)
    singleOf(::StellarTrustlineActionResolver)
    singleOf(::SwapProvidersRepository)
    singleOf(::AndroidUnstoppableProviderTitles) bind UnstoppableProviderTitles::class
    singleOf(::AssetFiatRateService)
    factoryOf(::FetchSwapQuotesUseCase)
    factoryOf(::IterativeExactOutSearch)
    viewModelOf(::MultiSwapExchangesViewModel)
    viewModelOf(::SwapProvidersSettingsViewModel)
    viewModel { params ->
        MultiSwapExchangeViewModel(
            pendingMultiSwapId = params.get(),
            pendingMultiSwapStorage = get(),
            marketKit = get(),
            numberFormatter = get(),
            onChainMonitor = get(),
            swapQuoteService = get(),
            swapProvidersRepository = get(),
            fetchSwapQuotesUseCase = get(),
            timerService = TimerService(get()),
            syncPendingMultiSwapUseCase = get(),
            assetFiatRateService = get(),
            currencyManager = get(),
            adapterManager = get(),
            balanceHiddenManager = get(),
            walletManager = get(),
            walletUseCase = get(),
            accountManager = get(),
        )
    }
    viewModel { (otherSelectedToken: Token?, activeAccount: Account) ->
        SwapSelectCoinViewModel(
            otherSelectedToken = otherSelectedToken,
            activeAccount = activeAccount,
            dispatcherProvider = get()
        )
    }
}
