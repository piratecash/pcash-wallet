package cash.p.terminal.core.managers

import com.sun.net.httpserver.HttpServer
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

// The app forces OkHttp 4.12 while the kit is built against OkHttp 5: this runs the kit's client on the app's version.
class ThorchainKitOkHttpCompatibilityTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/thorchain/lastblock/thorchain") { exchange ->
            val body = """[{"thorchain": $BLOCK_HEIGHT}]""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun fetchLastBlockHeight_appOkHttpVersion_returnsHeightAndNotifiesListener() = runBlocking {
        val finishedCalls = AtomicInteger()
        val listener = object : EventListener() {
            override fun callEnd(call: Call) {
                finishedCalls.incrementAndGet()
            }
        }
        val provider = ThornodeApiProvider.create(
            listOf(URL("http://127.0.0.1:${server.address.port}/")),
            "thorchain",
            EventListener.Factory { listener },
        )

        val height = provider.fetchLastBlockHeight()

        assertEquals(BLOCK_HEIGHT, height)
        assertEquals(1, finishedCalls.get())
    }

    private companion object {
        const val BLOCK_HEIGHT = 123L
    }
}
