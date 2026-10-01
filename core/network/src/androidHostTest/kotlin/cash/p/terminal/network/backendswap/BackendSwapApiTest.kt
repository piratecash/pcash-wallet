package cash.p.terminal.network.backendswap

import cash.p.terminal.network.backendswap.data.entity.AssetDto
import cash.p.terminal.network.backendswap.data.entity.BackendSwapError
import cash.p.terminal.network.backendswap.data.entity.ConfirmRequestDto
import cash.p.terminal.network.backendswap.data.entity.CreateRequestDto
import cash.p.terminal.network.backendswap.data.entity.EstimateRequestDto
import cash.p.terminal.network.yifi.respondJson
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.test.assertFailsWith

class BackendSwapApiTest {

    @Test
    fun getProviders_sendsPathAndAppHeaders() = runTest {
        lateinit var seen: HttpRequestData
        val api = backendSwapApi(FakeAppHeadersProvider(appVersion = "9.9.9")) { request ->
            seen = request
            respondJson("[]")
        }

        api.getProviders()

        assertEquals("https://p.cash/v2/exchanges/providers", seen.url.toString())
        assertEquals(HttpMethod.Get, seen.method)
        assertEquals("9.9.9", seen.headers["App-Version"])
        assertEquals("signature", seen.headers["App-Signature"])
    }

    @Test
    fun getCurrencies_sendsProviderQueryParameter() = runTest {
        lateinit var seen: HttpRequestData
        val api = backendSwapApi { request ->
            seen = request
            respondJson("[]")
        }

        api.getCurrencies("changelly")

        assertEquals("https://p.cash/v2/exchanges/currencies?provider=changelly", seen.url.toString())
    }

    @Test
    fun estimate_postsToEstimatePath() = runTest {
        lateinit var seen: HttpRequestData
        val api = backendSwapApi { request ->
            seen = request
            respondJson("""{"amountFrom":"0.1","amountToWithFee":"1.5"}""")
        }

        val result = api.estimate(
            EstimateRequestDto("changelly", "float", AssetDto("bitcoin", "bitcoin"), AssetDto("ethereum", "ethereum"), "0.1")
        )

        assertEquals("https://p.cash/v2/exchanges/estimate", seen.url.toString())
        assertEquals(HttpMethod.Post, seen.method)
        assertEquals("1.5", result.amountToWithFee)
    }

    @Test
    fun create_sendsWalletHeaderAndFullBody() = runTest {
        lateinit var seen: HttpRequestData
        val api = backendSwapApi { request ->
            seen = request
            respondJson(CREATED_RESPONSE, HttpStatusCode.Created)
        }

        api.create("0xwallet", createRequest())

        assertEquals("https://p.cash/v2/exchanges", seen.url.toString())
        assertEquals(HttpMethod.Post, seen.method)
        assertEquals("0xwallet", seen.headers["Wallet-EVM-Address"])
        val body = Json.parseToJsonElement(seen.rawBody()).jsonObject
        assertEquals("recipient-1", body.getValue("recipient").jsonPrimitive.content)
        assertEquals("extra-1", body.getValue("recipientExtraId").jsonPrimitive.content)
    }

    @Test
    fun create_nullOptionalFields_omitsThemFromBody() = runTest {
        lateinit var seen: HttpRequestData
        val api = backendSwapApi { request ->
            seen = request
            respondJson(CREATED_RESPONSE, HttpStatusCode.Created)
        }

        api.create(
            "0xwallet",
            createRequest(recipientExtraId = null, refundAddress = null, refundExtraId = null, fromAddress = null, fromExtraId = null)
        )

        val body = Json.parseToJsonElement(seen.rawBody()).jsonObject
        assertEquals(
            setOf("provider", "type", "from", "to", "amount", "recipient", "clientRequestId", "signature"),
            body.keys,
        )
    }

    @Test
    fun getSwap_sendsWalletHeaderOnSwapPath() = runTest {
        lateinit var seen: HttpRequestData
        val api = backendSwapApi { request ->
            seen = request
            respondJson(
                """{"id":"order-1","status":"finished","amountToActual":"0.01234","provider":"changelly","updatedAt":"2026-09-28T10:00:00Z"}"""
            )
        }

        val result = api.getSwap("order-1", "0xwallet")

        assertEquals("https://p.cash/v2/exchanges/order-1", seen.url.toString())
        assertEquals("0xwallet", seen.headers["Wallet-EVM-Address"])
        assertEquals("finished", result.status)
        assertEquals("0.01234", result.amountToActual)
    }

    @Test
    fun confirm_sendsPatchWithWalletHeaderAndBody() = runTest {
        lateinit var seen: HttpRequestData
        val api = backendSwapApi { request ->
            seen = request
            respond("", HttpStatusCode.NoContent)
        }

        api.confirm(
            "order-1",
            "0xwallet",
            ConfirmRequestDto("0xhash", "0.1", "bc1addr", currency = AssetDto("bitcoin", "bitcoin")),
        )

        assertEquals("https://p.cash/v2/exchanges/order-1/confirm", seen.url.toString())
        assertEquals(HttpMethod.Patch, seen.method)
        assertEquals("0xwallet", seen.headers["Wallet-EVM-Address"])
    }

    @Test
    fun getProviders_401MessageError_throwsBackendSwapErrorWithMessage() = runTest {
        val error = errorFor("""{"message":"Unauthorized"}""", HttpStatusCode.Unauthorized)

        assertEquals(401, error.statusCode)
        assertEquals("Unauthorized", error.message)
        assertNull(error.code)
    }

    @Test
    fun create_409MessageError_throwsBackendSwapErrorWithMessage() = runTest {
        val error = errorFor("""{"message":"client request id already used"}""", HttpStatusCode.Conflict)

        assertEquals(409, error.statusCode)
        assertEquals("client request id already used", error.message)
    }

    @Test
    fun estimate_422AmountLimitsError_throwsBackendSwapErrorWithMinAndMax() = runTest {
        val error = amountLimitsErrorFor("minimum")

        assertEquals(422, error.statusCode)
        assertEquals("AMOUNT_LIMITS_ERROR", error.code)
        assertEquals("0.01266787", error.minAmount)
        assertEquals("23.01157834", error.maxAmount)
        assertEquals("minimum", error.limitType)
    }

    @Test
    fun estimate_422AmountLimitsErrorMaximum_throwsBackendSwapErrorWithMaximumLimitType() = runTest {
        assertEquals("maximum", amountLimitsErrorFor("maximum").limitType)
    }

    private suspend fun amountLimitsErrorFor(limitType: String) = errorFor(
        """
        {
          "errors": {"amount": "Amount is out of limits"},
          "error": {
            "code": "AMOUNT_LIMITS_ERROR",
            "message": "Amount is out of limits",
            "details": {"limitType": "$limitType", "limits": {"max": "23.01157834", "min": "0.01266787"}}
          }
        }
        """.trimIndent(),
        HttpStatusCode.UnprocessableEntity,
    )

    @Test
    fun getCurrencies_429RateLimitError_throwsBackendSwapErrorWithCode() = runTest {
        val error = errorFor(
            """{"error":{"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests"}}""",
            HttpStatusCode.TooManyRequests,
        )

        assertEquals(429, error.statusCode)
        assertEquals("RATE_LIMIT_EXCEEDED", error.code)
    }

    @Test
    fun getCurrencies_502ProviderBadGateway_throwsBackendSwapErrorWithCode() = runTest {
        val error = errorFor(
            """{"error":{"code":"PROVIDER_INTERNAL_ERROR","message":"Provider internal error"}}""",
            HttpStatusCode.BadGateway,
        )

        assertEquals(502, error.statusCode)
        assertEquals("PROVIDER_INTERNAL_ERROR", error.code)
    }

    @Test
    fun getProviders_undecodableErrorBody_throwsBackendSwapErrorWithOnlyStatusCode() = runTest {
        val error = errorFor("not json", HttpStatusCode.InternalServerError)

        assertEquals(500, error.statusCode)
        assertNull(error.code)
        assertNull(error.message)
        assertNull(error.minAmount)
        assertNull(error.limitType)
    }

    private suspend fun errorFor(body: String, status: HttpStatusCode): BackendSwapError {
        val api = backendSwapApi { respondJson(body, status) }
        return assertFailsWith<BackendSwapError> { api.getProviders() }
    }

    private fun createRequest(
        recipientExtraId: String? = "extra-1",
        refundAddress: String? = "refund-1",
        refundExtraId: String? = "refund-extra-1",
        fromAddress: String? = "from-1",
        fromExtraId: String? = "from-extra-1",
    ) = CreateRequestDto(
        provider = "changelly",
        type = "float",
        from = AssetDto("bitcoin", "bitcoin"),
        to = AssetDto("ethereum", "ethereum"),
        amount = "0.01",
        recipient = "recipient-1",
        recipientExtraId = recipientExtraId,
        refundAddress = refundAddress,
        refundExtraId = refundExtraId,
        fromAddress = fromAddress,
        fromExtraId = fromExtraId,
        clientRequestId = "req-1",
        signature = "0xsig",
    )

    private fun HttpRequestData.rawBody(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private companion object {
        const val CREATED_RESPONSE = """{"id":"order-1","status":"waiting"}"""
    }
}
