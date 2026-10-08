package cash.p.terminal.wallet.di

import android.content.Context
import android.os.storage.StorageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import cash.p.terminal.wallet.HSCache
import cash.p.terminal.wallet.favorites.MarketFavoritesMigration
import cash.p.terminal.wallet.storage.AssetInitialCoinsSource
import cash.p.terminal.wallet.storage.InitialCoinsCallback
import cash.p.terminal.wallet.storage.InitialCoinsSource
import cash.p.terminal.wallet.storage.MARKET_DATABASE_NAME
import cash.p.terminal.wallet.storage.MarketDatabase
import okhttp3.Cache
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

internal actual fun platformMarketModule(): Module = module {
    single(named(RETROFIT_OK_HTTP_CLIENT)) { marketOkHttpClient(httpCache(get())) }

    single<InitialCoinsSource> { AssetInitialCoinsSource(get()) }

    single<DataStore<Preferences>>(named(MARKET_FAVORITES_DATA_STORE)) {
        PreferenceDataStoreFactory.create(
            migrations = listOfNotNull(getOrNull<MarketFavoritesMigration>()),
            produceFile = { marketFavoritesFile(get()) },
        )
    }

    single {
        Room.databaseBuilder(get<Context>(), MarketDatabase::class.java, MARKET_DATABASE_NAME)
            .addCallback(InitialCoinsCallback(get()))
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
            .forceCreation()
    }
}

/** Same layout as `Context.dataStoreFile`, which lives in an artifact this module does not need. */
private fun marketFavoritesFile(context: Context) =
    File(context.filesDir, "datastore/$MARKET_FAVORITES_FILE_NAME")

private fun httpCache(context: Context): Cache? {
    (context.getSystemService(Context.STORAGE_SERVICE) as StorageManager?)?.let { storageManager ->
        val cacheDir = context.cacheDir
        var cacheQuotaBytes =
            storageManager.getCacheQuotaBytes(storageManager.getUuidForPath(cacheDir))
        if (cacheQuotaBytes == 0L) { // on some Xiaomi we get 0
            cacheQuotaBytes = 10 * 1024 * 1024
        }
        HSCache.cacheDir = cacheDir
        HSCache.cacheQuotaBytes = cacheQuotaBytes
    }

    return HSCache.cacheDir?.let { Cache(it, HSCache.cacheQuotaBytes) }
}
