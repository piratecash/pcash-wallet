package cash.p.terminal.network.yifi

import cash.p.terminal.network.yifi.data.entity.request.CreateSwapYiFiRequest
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

class YiFiRequestSerializationTest {

    @Test
    fun createSwap_nullableFieldsNull_sendsOnlyWhitelistedFieldsWithPartnerData() = runTest {
        val body = sentBody(request(rateId = null, refundAddress = null))

        assertEquals(
            setOf(
                "provider", "fromToken", "fromNetwork", "toToken", "toNetwork",
                "amount", "receiveAddress", "engine", "refCode",
            ),
            body.keys,
        )
        assertEquals("cex", body.getValue("engine").jsonPrimitive.content)
        assertEquals("BSS67FAY", body.getValue("refCode").jsonPrimitive.content)
    }

    @Test
    fun createSwap_allFieldsSet_includesRateIdAndRefundAddress() = runTest {
        val body = sentBody(request(rateId = "rate-1", refundAddress = "refund-1"))

        assertEquals("rate-1", body.getValue("rateId").jsonPrimitive.content)
        assertEquals("refund-1", body.getValue("refundAddress").jsonPrimitive.content)
    }

    @Test
    fun createSwap_longFractionAmount_sentAsUnquotedNumberVerbatim() = runTest {
        val rawBody = sentRawBody(request(amount = BigDecimal("0.123456789012345678")))

        val amount = Json.parseToJsonElement(rawBody).jsonObject.getValue("amount").jsonPrimitive
        assertFalse(amount.isString)
        assertEquals("0.123456789012345678", amount.content)
    }

    private suspend fun sentBody(request: CreateSwapYiFiRequest): JsonObject =
        Json.parseToJsonElement(sentRawBody(request)).jsonObject

    private suspend fun sentRawBody(request: CreateSwapYiFiRequest): String {
        var rawBody = ""
        val api = yiFiApi { sent ->
            rawBody = (sent.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respondJson(SWAP_RESPONSE, HttpStatusCode.Created)
        }
        api.createSwap(request)
        return rawBody
    }

    private fun request(
        amount: BigDecimal = BigDecimal("1.5"),
        rateId: String? = "rate-1",
        refundAddress: String? = null,
    ) = CreateSwapYiFiRequest(
        provider = "ff",
        fromToken = "BTC",
        fromNetwork = "BTC",
        toToken = "ETH",
        toNetwork = "ETH",
        amount = amount,
        receiveAddress = "0xreceive",
        rateId = rateId,
        refundAddress = refundAddress,
    )

    private companion object {
        const val SWAP_RESPONSE =
            """{"transactionId":"tx-1","provider":"ff","depositAddress":"bc1dep","extraIdDeposit":null,"receiveAmount":"0.3","expirationTime":null}"""
    }
}
