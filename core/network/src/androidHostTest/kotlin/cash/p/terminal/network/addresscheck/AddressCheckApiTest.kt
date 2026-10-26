package cash.p.terminal.network.addresscheck

import cash.p.terminal.network.addresscheck.api.ChainalysisApi
import cash.p.terminal.network.addresscheck.api.HashDitApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressCheckApiTest {

    private var body = ""
    private lateinit var request: HttpRequestData
    private var requestBody = ""

    private val client = HttpClient(
        MockEngine { incoming ->
            request = incoming
            requestBody = incoming.body.toByteArray().decodeToString()
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }

    @Test
    fun riskLevel_serverPayload_parsesLevelAndSendsTransactionSecurityRequest() = runTest {
        body = """{"code":"000000","data":{"risk_level":4,"risk_detail":[]},"status":"OK"}"""

        val level = HashDitApi(client, "https://hashdit.example/", "key").riskLevel(1, "0xabc")

        assertEquals(4, level)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://hashdit.example/transaction-security", request.url.toString())
        assertEquals("key", request.headers["X-API-KEY"])
        val sent = Json.parseToJsonElement(requestBody).jsonObject
        assertEquals(1, sent.getValue("chainId").jsonPrimitive.int)
        assertEquals("0xabc", sent.getValue("to").jsonPrimitive.content)
        assertEquals("0x", sent.getValue("data").jsonPrimitive.content)
    }

    @Test
    fun hasIdentifications_nonEmptyIdentifications_returnsTrue() = runTest {
        body = """{"identifications":[{"category":"sanctions","name":"SDN","description":"d","url":"u"}]}"""

        val result = ChainalysisApi(client, "https://chainalysis.example/api/risk/v1", "key").hasIdentifications("0xabc")

        assertTrue(result)
        assertEquals("https://chainalysis.example/api/risk/v1/address/0xabc", request.url.toString())
        assertEquals("key", request.headers["X-API-KEY"])
    }

    @Test
    fun hasIdentifications_emptyIdentifications_returnsFalse() = runTest {
        body = """{"identifications":[]}"""

        assertFalse(ChainalysisApi(client, "https://chainalysis.example/", "key").hasIdentifications("0xabc"))
    }
}
