package cash.p.terminal.network.backendswap

import cash.p.terminal.network.backendswap.api.BackendSwapApi
import cash.p.terminal.network.backendswap.data.mapper.BackendSwapMapper
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.data.AppHeadersProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal class FakeAppHeadersProvider(
    override val appVersion: String = "1.2.3",
    override val currentLanguage: String = "en",
    override val appSignature: String? = "signature",
) : AppHeadersProvider

// Same Json configuration as buildNetworkClient.
internal fun backendSwapApi(
    appHeadersProvider: AppHeadersProvider = FakeAppHeadersProvider(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): BackendSwapApi {
    val client = HttpClient(MockEngine { request -> handler(request) }) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
    return BackendSwapApi(client, appHeadersProvider)
}

internal fun backendSwapRepository(
    appHeadersProvider: AppHeadersProvider = FakeAppHeadersProvider(),
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): BackendSwapRepository =
    BackendSwapRepository(backendSwapApi(appHeadersProvider, handler), BackendSwapMapper())
