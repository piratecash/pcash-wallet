package cash.p.terminal.modules.settings.appcache

interface PlatformCacheCleaner {
    suspend fun clearHttp()
    suspend fun clearImages()
    suspend fun clearDatabase()
}
