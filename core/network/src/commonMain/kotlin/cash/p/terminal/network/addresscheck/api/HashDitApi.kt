package cash.p.terminal.network.addresscheck.api

import cash.p.terminal.network.addresscheck.data.HashDitTransactionSecurityRequest
import cash.p.terminal.network.addresscheck.data.HashDitTransactionSecurityResponse
import cash.p.terminal.network.data.perPhaseTimeouts
import cash.p.terminal.network.data.setJsonBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType

private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"

class HashDitApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) {
    suspend fun riskLevel(chainId: Int, to: String): Int =
        httpClient.post(baseUrl + "transaction-security") {
            expectSuccess = true
            perPhaseTimeouts(connectSeconds = 5)
            accept(ContentType.Application.Json)
            header("X-API-KEY", apiKey)
            setJsonBody(HashDitTransactionSecurityRequest(chainId, to, from = ZERO_ADDRESS, data = "0x"))
        }.body<HashDitTransactionSecurityResponse>().data.riskLevel
}
