package cash.p.terminal.core.nft

import cash.p.terminal.core.TestDispatcherProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NftLottieLoaderTest {

    private val dispatcher = StandardTestDispatcher()
    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun load_webUrl_requestsProxyWithPaddedUrlSafeBase64() = runTest(dispatcher) {
        val loader = loader { respond(JSON) }

        val json = loader.load("https://nft.fragment.com/gift/plushpepe-399.lottie.json?x=~~~>>>???")

        assertEquals(JSON, json)
        val url = requests.single().url
        assertEquals("c.tonapi.io", url.host)
        assertEquals("/json", url.encodedPath)
        assertEquals(
            "aHR0cHM6Ly9uZnQuZnJhZ21lbnQuY29tL2dpZnQvcGx1c2hwZXBlLTM5OS5sb3R0aWUuanNvbj94PX5-fj4-Pj8_Pw==",
            url.parameters["url"]
        )
    }

    @Test
    fun load_declaredLengthOverCap_returnsNull() = runTest(dispatcher) {
        val loader = loader {
            respond(JSON, headers = headersOf(HttpHeaders.ContentLength, "${MAX_LOTTIE_BYTES + 1}"))
        }

        assertNull(loader.load(URL))
    }

    @Test
    fun load_bodyOverCapWithoutLength_returnsNull() = runTest(dispatcher) {
        val loader = loader { respond(ByteArray(MAX_LOTTIE_BYTES + 1) { 'a'.code.toByte() }) }

        assertNull(loader.load(URL))
    }

    @Test
    fun load_bodyExactlyAtCap_returnsText() = runTest(dispatcher) {
        val loader = loader { respond(ByteArray(MAX_LOTTIE_BYTES) { 'a'.code.toByte() }) }

        assertEquals(MAX_LOTTIE_BYTES, loader.load(URL)?.length)
    }

    @Test
    fun load_httpError_returnsNull() = runTest(dispatcher) {
        val loader = loader { respond("""{"error":"illegal base64 data"}""", HttpStatusCode.BadRequest) }

        assertNull(loader.load(URL))
    }

    @Test
    fun load_networkFailure_returnsNull() = runTest(dispatcher) {
        val loader = loader { throw IOException("offline") }

        assertNull(loader.load(URL))
    }

    private fun loader(handler: suspend MockRequestHandleScope.() -> HttpResponseData) = NftLottieLoader(
        httpClient = HttpClient(MockEngine { request ->
            requests += request
            handler()
        }),
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
    )

    private companion object {
        const val URL = "https://nft.fragment.com/gift/a.lottie.json"
        const val JSON = """{"v":"5.5.2","fr":60}"""
    }
}
