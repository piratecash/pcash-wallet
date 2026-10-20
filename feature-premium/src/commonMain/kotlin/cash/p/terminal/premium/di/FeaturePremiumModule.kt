package cash.p.terminal.premium.di

import cash.p.terminal.premium.data.repository.PremiumUserRepository
import cash.p.terminal.premium.domain.usecase.ActivateTrialPremiumUseCase
import cash.p.terminal.premium.domain.usecase.CheckAdapterPremiumBalanceUseCase
import cash.p.terminal.premium.domain.usecase.CheckAdapterPremiumBalanceUseCaseImpl
import cash.p.terminal.premium.domain.usecase.CheckTrialPremiumUseCase
import cash.p.terminal.premium.domain.usecase.GetBnbAddressUseCase
import cash.p.terminal.premium.domain.usecase.GetBnbAddressUseCaseImpl
import cash.p.terminal.premium.domain.usecase.SeedToEvmAddressUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

const val PREMIUM_IS_DEBUG_QUALIFIER = "premiumIsDebug"

val featurePremiumModule = module {
    includes(platformPremiumModule())

    // Repositories
    factoryOf(::PremiumUserRepository)

    // Use Cases
    singleOf(::CheckAdapterPremiumBalanceUseCaseImpl) bind CheckAdapterPremiumBalanceUseCase::class
    singleOf(::GetBnbAddressUseCaseImpl) bind GetBnbAddressUseCase::class
    factoryOf(::SeedToEvmAddressUseCase)
    factory {
        CheckTrialPremiumUseCase(get(), get(), get(), get(named(PREMIUM_IS_DEBUG_QUALIFIER)))
    }
    factoryOf(::ActivateTrialPremiumUseCase)
}

/** Platform storage, settings, hardware seam and when the premium warm-up starts. */
internal expect fun platformPremiumModule(): Module
