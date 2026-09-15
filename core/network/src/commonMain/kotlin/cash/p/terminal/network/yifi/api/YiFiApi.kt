package cash.p.terminal.network.yifi.api

import cash.p.terminal.network.data.setJsonBody
import cash.p.terminal.network.yifi.data.entity.BackendYiFiResponseError
import cash.p.terminal.network.yifi.data.entity.YiFiChainsResponseDto
import cash.p.terminal.network.yifi.data.entity.YiFiErrorResponseDto
import cash.p.terminal.network.yifi.data.entity.YiFiPairsResponseDto
import cash.p.terminal.network.yifi.data.entity.YiFiQuoteResponseDto
import cash.p.terminal.network.yifi.data.entity.YiFiSwapDto
import cash.p.terminal.network.yifi.data.entity.YiFiTokensResponseDto
import cash.p.terminal.network.yifi.data.entity.YiFiTransactionDto
import cash.p.terminal.network.yifi.data.entity.request.CreateSwapYiFiRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.math.BigDecimal

internal class YiFiApi(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BASE_URL = "https://api.yifi.io/v1/"
        const val ENGINE = "cex"
        const val REF_CODE = "BSS67FAY"
        const val TOKENS_LIMIT = 100

        // Integrated directly, so their commission must not go through YiFi.
        val DEFAULT_EXCLUDED_PROVIDERS = listOf("changenow", "quickex")
    }

    suspend fun getChains(): YiFiChainsResponseDto {
        return httpClient.get {
            url(BASE_URL + "chains")
            accept(ContentType.Application.Json)
        }.parseYiFiResponse()
    }

    suspend fun searchTokens(network: String, search: String): YiFiTokensResponseDto {
        return httpClient.get {
            url(BASE_URL + "tokens")
            accept(ContentType.Application.Json)
            parameter("engine", ENGINE)
            parameter("network", network)
            parameter("search", search)
            parameter("limit", TOKENS_LIMIT)
        }.parseYiFiResponse()
    }

    suspend fun getPairs(
        fromToken: String,
        fromNetwork: String,
        toToken: String,
        toNetwork: String,
    ): YiFiPairsResponseDto {
        return httpClient.get {
            url(BASE_URL + "pairs")
            accept(ContentType.Application.Json)
            parameter("fromToken", fromToken)
            parameter("fromNetwork", fromNetwork)
            parameter("toToken", toToken)
            parameter("toNetwork", toNetwork)
        }.parseYiFiResponse()
    }

    suspend fun getQuote(
        tickerFrom: String,
        networkFrom: String,
        tickerTo: String,
        networkTo: String,
        amount: BigDecimal,
        excludedProviders: Set<String>,
    ): YiFiQuoteResponseDto {
        return httpClient.get {
            url(BASE_URL + "quote")
            accept(ContentType.Application.Json)
            parameter("tickerFrom", tickerFrom)
            parameter("networkFrom", networkFrom)
            parameter("tickerTo", tickerTo)
            parameter("networkTo", networkTo)
            parameter("amount", amount.toPlainString())
            parameter("type", ENGINE)
            parameter("sort", "noKyc")
            parameter(
                "excludeProviders",
                (DEFAULT_EXCLUDED_PROVIDERS + excludedProviders).distinct().joinToString(",")
            )
        }.parseYiFiResponse()
    }

    suspend fun createSwap(request: CreateSwapYiFiRequest): YiFiSwapDto {
        return httpClient.post {
            url(BASE_URL + "swap")
            accept(ContentType.Application.Json)
            setJsonBody(request.withPartnerFields(engine = ENGINE, refCode = REF_CODE))
        }.parseYiFiResponse()
    }

    suspend fun getTransaction(transactionId: String): YiFiTransactionDto {
        return httpClient.get {
            url(BASE_URL + "transactions/$transactionId")
            accept(ContentType.Application.Json)
        }.parseYiFiResponse()
    }
}

internal val yiFiJson = Json { ignoreUnknownKeys = true; isLenient = true }

// Decoded outside Ktor: its converter error embeds the raw body (order responses carry an
// accessToken) and the Logging plugin prints that error.
internal suspend inline fun <reified T : Any> HttpResponse.parseYiFiResponse(): T {
    val text = bodyAsText()
    try {
        if (status.isSuccess()) return yiFiJson.decodeFromString<T>(text)
        val error = yiFiJson.decodeFromString<YiFiErrorResponseDto>(text).error
        throw BackendYiFiResponseError(code = error?.code, message = error?.message, statusCode = status.value)
    } catch (_: SerializationException) {
        throw BackendYiFiResponseError(code = null, message = null, statusCode = status.value)
    }
}
