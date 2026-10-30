package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.settings.appcache.PlatformCacheCleaner
import cash.p.terminal.modules.settings.appcache.clearDesktopImageCaches
import cash.p.terminal.network.pirate.data.database.CacheDatabaseCleaner
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import okhttp3.Cache

internal class DesktopPlatformCacheCleaner(
    private val httpCache: Cache,
    private val cacheDatabaseCleaner: CacheDatabaseCleaner,
    private val dispatcherProvider: DispatcherProvider
) : PlatformCacheCleaner {

    override suspend fun clearHttp() {
        withContext(dispatcherProvider.io) {
            runCatching { httpCache.evictAll() }
        }
    }

    override suspend fun clearImages() {
        withContext(dispatcherProvider.io) {
            runCatching { clearDesktopImageCaches() }
        }
    }

    override suspend fun clearDatabase() {
        withContext(dispatcherProvider.io) {
            runCatching { cacheDatabaseCleaner.clear() }
        }
    }
}
