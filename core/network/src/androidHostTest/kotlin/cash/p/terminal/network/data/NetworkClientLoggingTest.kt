package cash.p.terminal.network.data

import cash.p.terminal.network.addresscheck.api.ChainalysisApi
import cash.p.terminal.network.addresscheck.api.HashDitApi
import cash.p.terminal.network.evmlabel.api.EvmLabelApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientLoggingTest {

    private val logged = mutableListOf<String>()
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "{}"

    private val client = HttpClient(
        MockEngine { request ->
            requests += request
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
    ) {
        install(HttpTimeout) { requestTimeoutMillis = 60_000 }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        installSanitizedLogging { logged += it }
    }

    @Test
    fun logging_credentialHeaders_neverPrintsTheValue() = runTest {
        client.get("https://example.com/x") {
            header("X-API-KEY", SECRET)
            header("x-api-key", SECRET)
            header("api-key", SECRET)
            header(HttpHeaders.Authorization, SECRET)
        }

        assertTrue(logged.isNotEmpty())
        assertFalse(logged.joinToString("\n").contains(SECRET))
        assertEquals(SECRET, requests.single().headers["api-key"])
    }

    @Test
    fun logging_queryCredentials_neverPrintsTheValue() = runTest {
        client.get("https://example.com/x?api_key=$SECRET&apiKey=$SECRET&other=visible")

        val output = logged.joinToString("\n")
        assertFalse(output.contains(SECRET))
        assertTrue(output.contains("other=visible"))
        assertTrue(requests.single().url.toString().contains("api_key=$SECRET"))
    }

    @Test
    fun logging_amlApis_neverPrintTheKey() = runTest {
        body = """{"data":{"risk_level":1}}"""
        HashDitApi(client, "https://hashdit.example/", SECRET).riskLevel(1, "0xabc")
        body = """{"identifications":[]}"""
        ChainalysisApi(client, "https://chainalysis.example/", SECRET).hasIdentifications("0xabc")

        assertEquals(2, requests.size)
        assertFalse(logged.joinToString("\n").contains(SECRET))
    }

    @Test
    fun timeouts_migratedApis_matchTheRetrofitClients() = runTest {
        body = """{"address_labels":1,"evm_method_labels":2}"""
        EvmLabelApi(client, "https://api.example.com").updatesStatus()
        body = """{"data":{"risk_level":1}}"""
        HashDitApi(client, "https://hashdit.example/", SECRET).riskLevel(1, "0xabc")
        body = """{"identifications":[]}"""
        ChainalysisApi(client, "https://chainalysis.example/", SECRET).hasIdentifications("0xabc")

        val configs = requests.map { it.getCapabilityOrNull(HttpTimeoutCapability) }
        assertEquals(listOf(60_000L, 5_000L, 5_000L), configs.map { it?.connectTimeoutMillis })
        assertEquals(listOf(60_000L, 60_000L, 60_000L), configs.map { it?.socketTimeoutMillis })
        assertEquals(
            List(3) { HttpTimeoutConfig.INFINITE_TIMEOUT_MS },
            configs.map { it?.requestTimeoutMillis },
        )
    }

    private companion object {
        const val SECRET = "s3cr3t-key-value"
    }
}
