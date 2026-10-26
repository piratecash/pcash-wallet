package cash.p.terminal.network.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun buildNetworkClient(debug: Boolean) = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    if (debug) installSanitizedLogging()
}

internal fun HttpClientConfig<*>.installSanitizedLogging(sink: (String) -> Unit = ::println) {
    install(Logging) {
        sanitizeHeader { name -> name.lowercase() in CREDENTIAL_HEADERS }
        logger = object : Logger {
            override fun log(message: String) {
                message.lineSequence().forEach { line -> sink(LOG_TAG + line.redactQueryCredentials()) }
            }
        }
    }
}

private const val LOG_TAG = "KtorHttpLogger: "
private val CREDENTIAL_HEADERS = setOf(
    HttpHeaders.Authorization.lowercase(),
    HttpHeaders.ProxyAuthorization.lowercase(),
    HttpHeaders.Cookie.lowercase(),
    HttpHeaders.SetCookie.lowercase(),
    "x-api-key",
    "api-key",
)
private val QUERY_CREDENTIAL = Regex("([?&](?:api_key|apikey)=)[^&\\s]+", RegexOption.IGNORE_CASE)

private fun String.redactQueryCredentials() = replace(QUERY_CREDENTIAL, "$1***")

inline fun <reified T> HttpRequestBuilder.setJsonBody(body: T) {
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(body)
}
