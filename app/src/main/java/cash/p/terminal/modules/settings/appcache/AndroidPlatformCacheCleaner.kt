package cash.p.terminal.modules.settings.appcache

import android.content.Context
import cash.p.terminal.wallet.HSCache
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class AndroidPlatformCacheCleaner(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : PlatformCacheCleaner {

    override suspend fun clearHttp() {
        withContext(dispatcherProvider.io) {
            runCatching {
                HSCache.cacheDir?.let { dir ->
                    if (dir.exists()) {
                        dir.listFiles()?.forEach { file ->
                            file.deleteRecursively()
                        }
                    }
                }
            }
        }
    }

    override suspend fun clearImages() {
        withContext(dispatcherProvider.io) {
            runCatching { clearImageCaches(context) }
        }
    }

    override suspend fun clearDatabase() {
        withContext(dispatcherProvider.io) {
            runCatching {
                context.deleteDatabase("db_cache")
            }
        }
    }
}
