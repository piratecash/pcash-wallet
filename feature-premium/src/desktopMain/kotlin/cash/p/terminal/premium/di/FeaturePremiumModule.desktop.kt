package cash.p.terminal.premium.di

import cash.p.terminal.premium.data.DesktopPremiumSettings
import cash.p.terminal.premium.domain.HardwareBnbAddressProvider
import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCaseImpl
import cash.p.terminal.wallet.Account
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Desktop has no hardware-card accounts. */
private object NoHardwareBnbAddressProvider : HardwareBnbAddressProvider {
    override suspend fun scanAddress(account: Account): String? = null
}

/** The premium database comes from the vault session (host graph); the warm-up follows the vault state. */
internal actual fun platformPremiumModule(): Module = module {
    single<HardwareBnbAddressProvider> { NoHardwareBnbAddressProvider }
    singleOf(::DesktopPremiumSettings) bind PremiumSettings::class
    singleOf(::CheckPremiumUseCaseImpl) bind CheckPremiumUseCase::class
    // Desktop has no debug build: the trial trusts its local countdown, as an Android release does.
    single(named(PREMIUM_IS_DEBUG_QUALIFIER)) { false }
}
