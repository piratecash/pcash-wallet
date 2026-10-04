package cash.p.terminal.network.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import cash.p.terminal.network.data.NetworkEnvironment
import cash.p.terminal.network.desktopAppDataFile
import cash.p.terminal.network.pirate.data.database.CACHE_DATABASE_NAME
import cash.p.terminal.network.pirate.data.database.CacheAppDatabase
import cash.p.terminal.network.pirate.data.database.CacheAppDatabaseConstructor
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformNetworkModule(): Module = module {
    single {
        NetworkEnvironment(
            applicationId = "cash.p.terminal",
            isDebug = false,
        )
    }
    single {
        Room.databaseBuilder<CacheAppDatabase>(
            name = desktopAppDataFile(CACHE_DATABASE_NAME).absolutePath,
            factory = CacheAppDatabaseConstructor::initialize,
        )
            .setDriver(BundledSQLiteDriver())
            .build()
    }
}
