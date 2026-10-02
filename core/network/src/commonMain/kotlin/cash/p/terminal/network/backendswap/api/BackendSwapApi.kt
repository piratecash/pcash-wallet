package cash.p.terminal.network.backendswap.api

import cash.p.terminal.network.backendswap.data.entity.BackendSwapError
import cash.p.terminal.network.backendswap.data.entity.BackendSwapErrorResponseDto
import cash.p.terminal.network.backendswap.data.entity.ConfirmRequestDto
import cash.p.terminal.network.backendswap.data.entity.CreateRequestDto
import cash.p.terminal.network.backendswap.data.entity.CreatedSwapDto
import cash.p.terminal.network.backendswap.data.entity.CurrencyDto
import cash.p.terminal.network.backendswap.data.entity.EstimateDto
import cash.p.terminal.network.backendswap.data.entity.EstimateRequestDto
import cash.p.terminal.network.backendswap.data.entity.ProviderInfoDto
import cash.p.terminal.network.backendswap.data.entity.SwapDto
import cash.p.terminal.network.backendswap.data.entity.firstValidationMessage
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.data.appHeaders
import cash.p.terminal.network.data.setJsonBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class BackendSwapApi(
    private val httpClient: HttpClient,
    private val appHeadersProvider: AppHeadersProvider,
) {
    private companion object {
        const val BASE_URL = "https://p.cash/v2/exchanges"
        const val WALLET_HEADER = "Wallet-EVM-Address"
    }

    suspend fun getProviders(): List<ProviderInfoDto> = httpClient.get {
        url("$BASE_URL/providers")
        appHeaders(appHeadersProvider)
    }.parseBackendSwapResponse()

    suspend fun getCurrencies(provider: String): List<CurrencyDto> = httpClient.get {
        url("$BASE_URL/currencies")
        parameter("provider", provider)
        appHeaders(appHeadersProvider)
    }.parseBackendSwapResponse()

    suspend fun estimate(request: EstimateRequestDto): EstimateDto = httpClient.post {
        url("$BASE_URL/estimate")
        appHeaders(appHeadersProvider)
        setJsonBody(request)
    }.parseBackendSwapResponse()

    suspend fun create(walletAddress: String, request: CreateRequestDto): CreatedSwapDto = httpClient.post {
        url(BASE_URL)
        header(WALLET_HEADER, walletAddress)
        appHeaders(appHeadersProvider)
        setJsonBody(request)
    }.parseBackendSwapResponse()

    suspend fun getSwap(id: String, walletAddress: String): SwapDto = httpClient.get {
        url("$BASE_URL/$id")
        header(WALLET_HEADER, walletAddress)
        appHeaders(appHeadersProvider)
    }.parseBackendSwapResponse()

    suspend fun confirm(id: String, walletAddress: String, request: ConfirmRequestDto) {
        val response = httpClient.patch {
            url("$BASE_URL/$id/confirm")
            header(WALLET_HEADER, walletAddress)
            appHeaders(appHeadersProvider)
            setJsonBody(request)
        }
        if (!response.status.isSuccess()) throw response.toBackendSwapError()
    }
}

private val backendSwapJson = Json { ignoreUnknownKeys = true; isLenient = true }

private suspend inline fun <reified T : Any> HttpResponse.parseBackendSwapResponse(): T {
    return if (status.isSuccess()) body<T>() else throw toBackendSwapError()
}

private suspend fun HttpResponse.toBackendSwapError(): BackendSwapError {
    val dto = try {
        backendSwapJson.decodeFromString<BackendSwapErrorResponseDto>(bodyAsText())
    } catch (_: SerializationException) {
        null
    }
    return BackendSwapError(
        statusCode = status.value,
        code = dto?.error?.code,
        message = dto?.error?.message ?: dto?.message ?: dto.firstValidationMessage(),
        minAmount = dto?.error?.details?.limits?.min,
        maxAmount = dto?.error?.details?.limits?.max,
        limitType = dto?.error?.details?.limitType,
    )
}
