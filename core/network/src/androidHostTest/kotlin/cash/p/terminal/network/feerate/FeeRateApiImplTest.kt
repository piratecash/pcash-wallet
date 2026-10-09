package cash.p.terminal.network.feerate

import cash.p.terminal.network.feerate.api.BitcoinFeeRates
import cash.p.terminal.network.feerate.api.FeeRateApi
import cash.p.terminal.network.feerate.api.FeeRateApiImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.test.assertFailsWith

class FeeRateApiImplTest {

    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun bitcoinFeeRates_torDisabled_readsMempoolRecommended() = runTest {
        val api = api { respondJson(MEMPOOL_BODY) }

        assertEquals(BitcoinFeeRates(halfHourFee = 8, minimumFee = 2), api.bitcoinFeeRates(torEnabled = false))
        assertEquals(listOf(MEMPOOL_URL), requestedUrls())
    }

    @Test
    fun bitcoinFeeRates_torEnabled_readsCeiledBlockstreamEstimates() = runTest {
        val api = api { respondJson(BLOCKSTREAM_BODY) }

        assertEquals(BitcoinFeeRates(halfHourFee = 4, minimumFee = 1), api.bitcoinFeeRates(torEnabled = true))
        assertEquals(listOf(BLOCKSTREAM_URL), requestedUrls())
    }

    @Test
    fun bitcoinFeeRates_httpError_throws() = runTest {
        val api = api { respondJson(MEMPOOL_BODY, HttpStatusCode.TooManyRequests) }

        assertFailsWith<ClientRequestException> { api.bitcoinFeeRates(torEnabled = false) }
    }

    @Test
    fun bitcoinFeeRates_torEnabled_ignoresUnusedTargets() = runTest {
        val api = api { respondJson("""{"2":null,"3":7.01,"5":"n/a","8":2.5}""") }

        assertEquals(BitcoinFeeRates(halfHourFee = 8, minimumFee = 3), api.bitcoinFeeRates(torEnabled = true))
    }

    @Test
    fun dashFeeRate_blockCypherSuccess_convertsPerKbToPerByteCeil() = runTest {
        val api = api { respondJson(BLOCKCYPHER_BODY) }

        assertEquals(24, api.dashFeeRate(torEnabled = false))
        assertEquals(listOf(BLOCKCYPHER_URL), requestedUrls())
    }

    // The 10_000 per-kb default and the static fallback both yield 10, so this only pins that `{}` is accepted.
    @Test
    fun dashFeeRate_fieldMissing_usesDefaultPerKb() = runTest {
        val api = api { respondJson("{}") }

        assertEquals(10, api.dashFeeRate(torEnabled = false))
    }

    @Test
    fun dashFeeRate_httpError_returnsFallback() = runTest {
        val api = api { respondJson(BLOCKCYPHER_BODY, HttpStatusCode.TooManyRequests) }

        assertEquals(DASH_FALLBACK, api.dashFeeRate(torEnabled = false))
    }

    @Test
    fun dashFeeRate_noResponseWithin10s_returnsFallback() = runTest {
        val api = api { awaitCancellation() }

        assertEquals(DASH_FALLBACK, api.dashFeeRate(torEnabled = false))
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test
    fun dashFeeRate_callerCancelled_doesNotReturnFallback() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val api = api {
            requestStarted.complete(Unit)
            awaitCancellation()
        }
        var result: Int? = null

        val caller = launch { result = api.dashFeeRate(torEnabled = false) }
        requestStarted.await()
        caller.cancelAndJoin()

        assertNull(result)
    }

    @Test
    fun requests_torEnabled_useTorTimeouts() = runTest {
        val api = api { request -> respondJson(bodyFor(request)) }

        api.bitcoinFeeRates(torEnabled = true)
        api.dashFeeRate(torEnabled = true)

        assertEquals(listOf(BLOCKSTREAM_URL, BLOCKCYPHER_URL), requestedUrls())
        assertTimeouts(connect = 30_000, socket = 30_000, request = 60_000)
    }

    @Test
    fun requests_torDisabled_useDirectTimeouts() = runTest {
        val api = api { request -> respondJson(bodyFor(request)) }

        api.bitcoinFeeRates(torEnabled = false)
        api.dashFeeRate(torEnabled = false)

        assertEquals(listOf(MEMPOOL_URL, BLOCKCYPHER_URL), requestedUrls())
        assertTimeouts(connect = 5_000, socket = 5_000, request = HttpTimeoutConfig.INFINITE_TIMEOUT_MS)
    }

    private fun assertTimeouts(connect: Long, socket: Long, request: Long) {
        requests.forEach {
            val timeouts = it.getCapabilityOrNull(HttpTimeoutCapability)
            assertEquals(connect, timeouts?.connectTimeoutMillis)
            assertEquals(socket, timeouts?.socketTimeoutMillis)
            assertEquals(request, timeouts?.requestTimeoutMillis)
        }
    }

    private fun requestedUrls() = requests.map { it.url.toString() }

    private fun bodyFor(request: HttpRequestData) = when (request.url.toString()) {
        MEMPOOL_URL -> MEMPOOL_BODY
        BLOCKSTREAM_URL -> BLOCKSTREAM_BODY
        else -> BLOCKCYPHER_BODY
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    // The engine runs on the test scheduler so a handler that never answers is resolved in virtual time.
    private fun TestScope.api(handler: MockRequestHandler): FeeRateApi {
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    requests += request
                    handler(request)
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        return FeeRateApiImpl(client)
    }

    private companion object {
        const val MEMPOOL_URL = "https://mempool.space/api/v1/fees/recommended"
        const val BLOCKSTREAM_URL = "https://blockstream.info/api/fee-estimates"
        const val BLOCKCYPHER_URL = "https://api.blockcypher.com/v1/dash/main"
        const val DASH_FALLBACK = 10

        const val MEMPOOL_BODY =
            """{"fastestFee":12,"halfHourFee":8,"hourFee":5,"economyFee":3,"minimumFee":2}"""
        const val BLOCKSTREAM_BODY =
            """{"1":5.2,"2":4.4,"3":3.099,"4":2.9,"5":1.8,"6":1.2,"7":0.9,"8":0.25,"144":0.1}"""
        const val BLOCKCYPHER_BODY =
            """{"name":"DASH.main","height":2300000,"high_fee_per_kb":23969,"medium_fee_per_kb":12000}"""
    }
}
