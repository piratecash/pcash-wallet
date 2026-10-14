package cash.p.terminal.wallet.di

import cash.p.terminal.wallet.WalletFactory
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val useCasesModule = module {
    singleOf(::WalletFactory)
}
