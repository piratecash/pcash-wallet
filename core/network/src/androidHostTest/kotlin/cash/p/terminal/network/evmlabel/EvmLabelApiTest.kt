package cash.p.terminal.network.evmlabel

import cash.p.terminal.network.evmlabel.api.EvmLabelApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EvmLabelApiTest {

    private val requested = mutableListOf<String>()
    private var status = HttpStatusCode.OK
    private var body = ""

    private val api = EvmLabelApi(
        HttpClient(
            MockEngine { request ->
                requested += request.url.toString()
                respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        },
        "https://api.example.com",
    )

    @Test
    fun updatesStatus_serverPayload_parsesTimestamps() = runTest {
        body = """{"address_labels":1700000000,"evm_method_labels":1700000500,"extra":"ignored"}"""

        val result = api.updatesStatus()

        assertEquals(1700000000L, result.addressLabels)
        assertEquals(1700000500L, result.evmMethodLabels)
        assertEquals(listOf("https://api.example.com/v1/status/updates"), requested)
    }

    @Test
    fun evmMethodLabels_serverPayload_parsesMethodIdAndLabel() = runTest {
        body = """[{"method_id":"0xa9059cbb","label":"Transfer"},{"method_id":"0x095ea7b3","label":"Approve"}]"""

        val result = api.evmMethodLabels()

        assertEquals(listOf("0xa9059cbb" to "Transfer", "0x095ea7b3" to "Approve"), result.map { it.methodId to it.label })
        assertEquals(listOf("https://api.example.com/v1/evm-method-labels"), requested)
    }

    @Test
    fun evmAddressLabels_serverPayload_parsesAddressAndLabel() = runTest {
        body = """[{"address":"0x579fedB9253ccA1b3114d5e2fA44F8158d61e436","label":"Token Bridge"}]"""

        val result = api.evmAddressLabels()

        assertEquals("0x579fedB9253ccA1b3114d5e2fA44F8158d61e436", result.single().address)
        assertEquals("Token Bridge", result.single().label)
        assertEquals(listOf("https://api.example.com/v1/addresses/labels"), requested)
    }

    @Test
    fun evmMethodLabels_serverError_throws() = runTest {
        status = HttpStatusCode.BadRequest

        assertThrows(ClientRequestException::class.java) { runBlocking { api.evmMethodLabels() } }
    }
}
