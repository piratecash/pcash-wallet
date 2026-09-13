package cash.p.terminal.feature.miniapp.di

import cash.p.terminal.feature.miniapp.data.api.MiniAppApi
import cash.p.terminal.feature.miniapp.domain.usecase.CheckRequiredTokensUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.ConnectMiniAppWalletUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.GetSpecialProposalDataUseCase
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppViewModel
import cash.p.terminal.feature.miniapp.ui.miniapp.MiniAppViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val featureMiniAppModule = module {
    // API
    singleOf(::MiniAppApi)

    // Use cases
    singleOf(::GetSpecialProposalDataUseCase)
    singleOf(::CheckRequiredTokensUseCase)
    singleOf(::ConnectMiniAppWalletUseCase)

    // ViewModels
    viewModelOf(::MiniAppViewModel)
    viewModel { params ->
        ConnectMiniAppViewModel(
            checkPremiumUseCase = get(),
            getSpecialProposalDataUseCase = get(),
            checkRequiredTokensUseCase = get(),
            createRequiredTokensUseCase = get(),
            connectMiniAppWalletUseCase = get(),
            accountManager = get(),
            marketKitWrapper = get(),
            balanceService = get(named("wallet")),
            uniqueCodeStorage = get(),
            savedStateHandle = params.get()
        )
    }
}
