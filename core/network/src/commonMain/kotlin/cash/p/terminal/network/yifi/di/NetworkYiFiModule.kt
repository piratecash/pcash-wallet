package cash.p.terminal.network.yifi.di

import cash.p.terminal.network.yifi.api.YiFiApi
import cash.p.terminal.network.yifi.data.mapper.YiFiMapper
import cash.p.terminal.network.yifi.data.repository.YiFiRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val networkYiFiModule = module {
    singleOf(::YiFiApi)
    singleOf(::YiFiMapper)
    singleOf(::YiFiRepository)
}
