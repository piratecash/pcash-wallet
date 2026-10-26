package cash.p.terminal.network.data

import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder

/** The OkHttp timeouts of the former Retrofit clients: connect and read per phase, no total deadline. */
internal fun HttpRequestBuilder.perPhaseTimeouts(connectSeconds: Long, readSeconds: Long = 60) = timeout {
    connectTimeoutMillis = connectSeconds * 1_000
    socketTimeoutMillis = readSeconds * 1_000
    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
}
