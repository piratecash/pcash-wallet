package cash.p.terminal.premium.di

import cash.p.terminal.premium.data.TangemBnbAddressProvider
import cash.p.terminal.premium.data.UserPremiumSettings
import cash.p.terminal.premium.data.database.PremiumDatabaseProvider
import cash.p.terminal.premium.data.database.StaticPremiumDatabaseProvider
import cash.p.terminal.premium.data.database.createPremiumDatabase
import cash.p.terminal.premium.domain.HardwareBnbAddressProvider
import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCaseImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.new
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual fun platformPremiumModule(): Module = module {
    single { createPremiumDatabase(get()) }
    singleOf(::StaticPremiumDatabaseProvider) bind PremiumDatabaseProvider::class

    singleOf(::UserPremiumSettings) bind PremiumSettings::class
    singleOf(::TangemBnbAddressProvider) bind HardwareBnbAddressProvider::class

    // Created eagerly at startup so the per-account premium cache is warmed in the background before the
    // wallet-list / switcher screens are opened (they then read a warm cache instead of scanning on open).
    single(createdAtStart = true) {
        new(::CheckPremiumUseCaseImpl).apply { startWarmUp() }
    } bind CheckPremiumUseCase::class
}
