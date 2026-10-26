package cash.p.terminal.network.evmlabel.api

import cash.p.terminal.network.evmlabel.data.EvmAddressLabelDto
import cash.p.terminal.network.evmlabel.data.EvmLabelUpdatesStatus
import cash.p.terminal.network.evmlabel.data.EvmMethodLabelDto
import cash.p.terminal.network.data.perPhaseTimeouts
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get

class EvmLabelApi(
    private val httpClient: HttpClient,
    marketApiBaseUrl: String,
) {
    private val apiUrl = "$marketApiBaseUrl/v1/"

    suspend fun updatesStatus(): EvmLabelUpdatesStatus = get("status/updates")

    suspend fun evmMethodLabels(): List<EvmMethodLabelDto> = get("evm-method-labels")

    suspend fun evmAddressLabels(): List<EvmAddressLabelDto> = get("addresses/labels")

    private suspend inline fun <reified T> get(path: String): T =
        httpClient.get(apiUrl + path) {
            expectSuccess = true
            perPhaseTimeouts(connectSeconds = 60)
        }.body()
}
