package cash.p.terminal.di

import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.modules.market.topnftcollections.TopNftCollectionsRepository
import cash.p.terminal.modules.market.topplatforms.TopPlatformsRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val repositoryModule = module {
    factoryOf(::EvmTransactionRepository)
    factoryOf(::TopNftCollectionsRepository)
    factoryOf(::TopPlatformsRepository)
}
