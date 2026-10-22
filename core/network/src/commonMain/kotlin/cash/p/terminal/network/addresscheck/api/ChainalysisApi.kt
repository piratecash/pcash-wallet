package cash.p.terminal.network.addresscheck.api

import cash.p.terminal.network.addresscheck.data.ChainalysisAddressResponse
import cash.p.terminal.network.data.perPhaseTimeouts
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments

class ChainalysisApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) {
    suspend fun hasIdentifications(address: String): Boolean =
        httpClient.get(baseUrl) {
            expectSuccess = true
            perPhaseTimeouts(connectSeconds = 5)
            url { appendPathSegments("address", address) }
            accept(ContentType.Application.Json)
            header("X-API-KEY", apiKey)
        }.body<ChainalysisAddressResponse>().identifications.isNotEmpty()
}
