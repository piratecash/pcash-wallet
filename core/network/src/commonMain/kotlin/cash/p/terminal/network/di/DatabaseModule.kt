package cash.p.terminal.network.di

import cash.p.terminal.network.pirate.data.database.CacheAppDatabase
import cash.p.terminal.network.pirate.data.database.CacheDatabaseCleaner
import org.koin.dsl.module

internal val databaseModule = module {
    single { get<CacheAppDatabase>().changeNowCoinDao() }
    single { CacheDatabaseCleaner(get()) }
}
