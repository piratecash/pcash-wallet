package cash.p.terminal.network.backendswap.di

import cash.p.terminal.network.backendswap.api.BackendSwapApi
import cash.p.terminal.network.backendswap.data.mapper.BackendSwapMapper
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val networkBackendSwapModule = module {
    singleOf(::BackendSwapApi)
    singleOf(::BackendSwapMapper)
    singleOf(::BackendSwapRepository)
}
