package cash.p.terminal.feature.miniapp.data.api

import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.data.setJsonBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext

class MiniAppApi(
    private val httpClient: HttpClient,
    private val appHeadersProvider: AppHeadersProvider,
    private val dispatcherProvider: DispatcherProvider
) {

    internal companion object {
        const val API_VERSION = 2
    }

    suspend fun getUserProfile(
        jwt: String,
        endpoint: String
    ): ProfileResponseDto = withContext(dispatcherProvider.io) {
        httpClient.get {
            url("${endpoint}miniapp/users")
            authHeaders(jwt)
        }.parseResponse()
    }

    suspend fun getEvmNonce(
        jwt: String,
        endpoint: String
    ): EvmNonceResponseDto = withContext(dispatcherProvider.io) {
        httpClient.get {
            url("${endpoint}miniapp/users/wallet/evm/nonce")
            authHeaders(jwt)
        }.parseResponse()
    }

    suspend fun submitPCashWallet(
        jwt: String,
        endpoint: String,
        request: PCashWalletRequestDto
    ): PCashWalletResponseDto = withContext(dispatcherProvider.io) {
        httpClient.post {
            url("${endpoint}miniapp/users/wallet/pcash")
            authHeaders(jwt)
            setJsonBody(request)
        }.parseResponse()
    }

    private fun HttpRequestBuilder.authHeaders(jwt: String) {
        header(HttpHeaders.Authorization, "Bearer $jwt")
        appHeaders()
    }

    private fun HttpRequestBuilder.appHeaders() {
        header("App-Version", appHeadersProvider.appVersion)
        header(HttpHeaders.AcceptLanguage, appHeadersProvider.currentLanguage)
        appHeadersProvider.appSignature?.let { header("App-Signature", it) }
    }
}

private val errorJson = Json { ignoreUnknownKeys = true }

private suspend inline fun <reified T : Any> HttpResponse.parseResponse(): T {
    return if (status.isSuccess()) {
        body<T>()
    } else {
        val errorMessage = try {
            val body = bodyAsText()
            errorJson.decodeFromString<ErrorResponse>(body).message ?: status.description
        } catch (e: Exception) {
            status.description
        }
        throw MiniAppApiException(status.value, errorMessage)
    }
}

class MiniAppApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message) {
    val isJwtExpired: Boolean get() = statusCode == 401
}

@Serializable
private data class ErrorResponse(
    val message: String? = null
)
