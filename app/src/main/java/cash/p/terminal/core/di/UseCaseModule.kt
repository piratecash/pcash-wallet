package cash.p.terminal.core.di

import cash.p.terminal.core.policy.CompositeHardwareWalletTokenPolicy
import cash.p.terminal.core.policy.CompositeScanToAddUseCase
import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.core.usecase.AndroidMoneroWalletStorage
import cash.p.terminal.core.usecase.CheckGooglePlayUpdateUseCase
import cash.p.terminal.core.usecase.CreateHardwareWalletUseCase
import cash.p.terminal.core.usecase.CreateTrezorWalletUseCase
import cash.p.terminal.core.usecase.GetMoneroWalletFilesNameUseCase
import cash.p.terminal.core.usecase.MoneroRestoreHeightResolver
import cash.p.terminal.core.usecase.MoneroWalletStorage
import cash.p.terminal.domain.usecase.ClearLegacyZcashDataUseCase
import cash.p.terminal.domain.usecase.DeleteAllContactsUseCase
import cash.p.terminal.domain.usecase.ResetUseCase
import cash.p.terminal.manager.ITorConnectionStatusUseCase
import cash.p.terminal.modules.pin.SendZecOnDuressUseCase
import cash.p.terminal.modules.softwareupdate.domain.GooglePlayUpdateAvailabilityProvider
import cash.p.terminal.modules.tor.TorConnectionStatusUseCase
import cash.p.terminal.tangem.domain.usecase.ICreateHardwareWalletUseCase
import cash.p.terminal.trezor.domain.usecase.ICreateTrezorWalletUseCase
import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightResolver
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val useCaseModule = module {
    factoryOf(::MoneroRestoreHeightResolver) bind TrezorMoneroRestoreHeightResolver::class
    factoryOf(::AndroidMoneroWalletStorage) bind MoneroWalletStorage::class
    singleOf(::AddMoneroToTrezorAccountUseCase)
    factoryOf(::CheckGooglePlayUpdateUseCase) bind GooglePlayUpdateAvailabilityProvider::class
    factoryOf(::CreateHardwareWalletUseCase) bind ICreateHardwareWalletUseCase::class
    factoryOf(::CreateTrezorWalletUseCase) bind ICreateTrezorWalletUseCase::class
    factoryOf(::GetMoneroWalletFilesNameUseCase) bind IGetMoneroWalletFilesNameUseCase::class
    singleOf(::TorConnectionStatusUseCase) bind ITorConnectionStatusUseCase::class
    factoryOf(::ClearLegacyZcashDataUseCase)
    singleOf(::DeleteAllContactsUseCase)
    singleOf(::ResetUseCase)
    singleOf(::SendZecOnDuressUseCase)
    singleOf(::CompositeHardwareWalletTokenPolicy) bind HardwareWalletTokenPolicy::class
    single<ScanToAddUseCase> {
        CompositeScanToAddUseCase(
            get(),
            get(named("tangem")),
            get(named("trezor")),
            get(),
        )
    }
}
