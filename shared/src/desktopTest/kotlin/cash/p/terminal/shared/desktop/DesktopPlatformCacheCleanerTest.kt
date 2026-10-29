package cash.p.terminal.shared.desktop

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.network.pirate.data.database.CacheDatabaseCleaner
import com.sun.net.httpserver.HttpServer
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.assertEquals

class DesktopPlatformCacheCleanerTest {

    private val databaseCleaner = mockk<CacheDatabaseCleaner>(relaxed = true)

    @Test
    fun clearHttp_cachedResponse_emptiesCache() = runTest {
        val directory = Files.createTempDirectory("market-http-cache").toFile()
        val cache = Cache(directory, 1024 * 1024)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.responseHeaders.add("Cache-Control", "max-age=60")
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            start()
        }
        OkHttpClient.Builder().cache(cache).build()
            .newCall(Request.Builder().url("http://127.0.0.1:${server.address.port}/prices").build())
            .execute().use { it.body.string() }
        assertEquals(1, cache.urls().asSequence().count())

        cleaner(cache).clearHttp()

        assertEquals(0, cache.urls().asSequence().count())
        server.stop(0)
        cache.close()
        directory.deleteRecursively()
    }

    @Test
    fun clearDatabase_delegatesToCacheDatabaseCleaner() = runTest {
        val cache = Cache(Files.createTempDirectory("market-http-cache").toFile(), 1024)

        cleaner(cache).clearDatabase()

        coVerify(exactly = 1) { databaseCleaner.clear() }
        cache.close()
    }

    private fun TestScope.cleaner(cache: Cache): DesktopPlatformCacheCleaner {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DesktopPlatformCacheCleaner(
            cache, databaseCleaner, TestDispatcherProvider(dispatcher, TestScope(dispatcher))
        )
    }
}
