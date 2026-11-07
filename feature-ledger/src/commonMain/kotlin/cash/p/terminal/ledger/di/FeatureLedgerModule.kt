package cash.p.terminal.ledger.di

import cash.p.terminal.ledger.account.LedgerAccountAddressVerifier
import cash.p.terminal.ledger.account.LedgerAccountApp
import cash.p.terminal.ledger.account.LedgerAccountKeyFetcher
import cash.p.terminal.ledger.account.LedgerSolanaClearSigning
import cash.p.terminal.ledger.bitcoin.LedgerBtcAddressVerifier
import cash.p.terminal.ledger.bitcoin.LedgerBtcApp
import cash.p.terminal.ledger.bitcoin.LedgerBtcKeyFetcher
import cash.p.terminal.ledger.domain.LedgerAddressVerification
import cash.p.terminal.ledger.domain.LedgerAddressVerifier
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerAppKeyFetchers
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledger.domain.LedgerHardwareWalletTokenPolicy
import cash.p.terminal.ledger.domain.LedgerKeyReader
import cash.p.terminal.ledger.domain.LedgerScanToAddUseCase
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledger.ethereum.LedgerCalClient
import cash.p.terminal.ledger.ethereum.LedgerEvmAddressVerifier
import cash.p.terminal.ledger.ethereum.LedgerEvmKeyFetcher
import cash.p.terminal.ledger.ethereum.LedgerTokenDescriptorStore
import cash.p.terminal.ledger.ethereum.LedgerTokenDescriptorSync
import cash.p.terminal.ledger.monero.LedgerMoneroKeyFetcher
import cash.p.terminal.ledger.ui.LedgerVerifyAddressViewModel
import cash.p.terminal.ledger.ui.LedgerWalletViewModel
import cash.p.terminal.ledger.zcash.LedgerZcashAddressVerifier
import cash.p.terminal.ledger.zcash.LedgerZcashKeyFetcher
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val LEDGER_TOKEN_DESCRIPTORS = named("ledgerTokenDescriptors")

/** Every Ledger key fetcher; DI and token eligibility both read this one list, so they cannot disagree. */
val ledgerAppKeyFetchers = LedgerAppKeyFetchers(
    LedgerBtcApp.entries.map(::LedgerBtcKeyFetcher) +
        LedgerEvmKeyFetcher() +
        LedgerAccountApp.entries.map(::LedgerAccountKeyFetcher) +
        LedgerZcashKeyFetcher() +
        LedgerMoneroKeyFetcher()
)

/**
 * Needs a [LedgerConnector] and a [LEDGER_TOKEN_DESCRIPTORS] data store from the platform;
 * address verifiers bind with `bind`.
 */
val featureLedgerModule = module {
    single { ledgerAppKeyFetchers }
    singleOf(::LedgerBtcAddressVerifier) bind LedgerAddressVerifier::class
    singleOf(::LedgerEvmAddressVerifier) bind LedgerAddressVerifier::class
    singleOf(::LedgerAccountAddressVerifier) bind LedgerAddressVerifier::class
    singleOf(::LedgerZcashAddressVerifier) bind LedgerAddressVerifier::class

    single { LedgerTokenDescriptorStore(get(LEDGER_TOKEN_DESCRIPTORS)) }
    singleOf(::LedgerCalClient)
    singleOf(::LedgerTokenDescriptorSync)
    singleOf(::LedgerSolanaClearSigning)

    single { LedgerSession(get<LedgerConnector>()) }
    singleOf(::LedgerUserOperations)
    singleOf(::LedgerKeyReader)
    singleOf(::LedgerAppAccess)
    singleOf(::LedgerHardwareWalletTokenPolicy)
    single { LedgerAddressVerification(getAll(), get()) }
    factoryOf(::LedgerScanToAddUseCase)
    factory<ScanToAddUseCase>(named("ledger")) { get<LedgerScanToAddUseCase>() }

    viewModel { params -> LedgerWalletViewModel(params.get(), get(), get()) }
    viewModel { params -> LedgerVerifyAddressViewModel(params.get(), get()) }
}
