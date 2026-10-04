package cash.p.terminal.wallet.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import cash.p.terminal.network.desktopAppDataFile
import cash.p.terminal.wallet.MarketApiConfig
import cash.p.terminal.wallet.favorites.MarketFavoritesChangeListener
import cash.p.terminal.wallet.storage.ClasspathInitialCoinsSource
import cash.p.terminal.wallet.storage.InitialCoinsCallback
import cash.p.terminal.wallet.storage.InitialCoinsSource
import cash.p.terminal.wallet.storage.MARKET_DATABASE_NAME
import cash.p.terminal.wallet.storage.MarketDatabase
import cash.p.terminal.wallet.storage.MarketDatabaseConstructor
import kotlinx.coroutines.Dispatchers
import okhttp3.Cache
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024 * 1024

/** Desktop has no watchlist widgets to refresh. */
private object DesktopMarketFavoritesChangeListener : MarketFavoritesChangeListener {
    override fun onFavoritesChanged() = Unit
}

internal actual fun platformMarketModule(): Module = module {
    single { MarketApiConfig(MarketApiConfig.PROD_BASE_URL, MarketApiConfig.API_KEY) }

    single(named(RETROFIT_OK_HTTP_CLIENT)) {
        marketOkHttpClient(Cache(desktopAppDataFile("market-http-cache"), HTTP_CACHE_SIZE_BYTES))
    }

    single<InitialCoinsSource> { ClasspathInitialCoinsSource() }

    single<DataStore<Preferences>>(named(MARKET_FAVORITES_DATA_STORE)) {
        PreferenceDataStoreFactory.create(
            produceFile = { desktopAppDataFile(MARKET_FAVORITES_FILE_NAME) }
        )
    }

    single<MarketFavoritesChangeListener> { DesktopMarketFavoritesChangeListener }

    single {
        Room.databaseBuilder<MarketDatabase>(
            name = desktopAppDataFile(MARKET_DATABASE_NAME).absolutePath,
            factory = MarketDatabaseConstructor::initialize,
        )
            .setDriver(BundledSQLiteDriver())
            .addCallback(InitialCoinsCallback(get()))
            .fallbackToDestructiveMigration(dropAllTables = false)
            // A blocking DAO nested in a transaction must reach Room's `useConnection` undispatched,
            // so Room recovers the transaction's connection from its thread local.
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .build()
            .forceCreation()
    }
}
