package cash.p.terminal.trezor.client

import cash.p.terminal.trezorkit.client.TrezorEthereumDefinitionRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrezorEthereumDefinitionsProviderImplTest {

    @Test
    fun getDefinitions_displayRequest_fetchesExactUrlsAndKeepsPartialResult() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = client { url ->
            requestedUrls += url
            when {
                url.endsWith("network.dat") -> respond(byteArrayOf(1, 2))
                url.contains("/token-") -> respond(ByteArray(0), HttpStatusCode.NotFound)
                else -> respond(byteArrayOf(3, 4))
            }
        }
        val provider = TrezorEthereumDefinitionsProviderImpl(client)
        val address = "ab".repeat(20)

        val result = provider.getDefinitions(
            TrezorEthereumDefinitionRequest(4663, address, "a9059cbb"),
        )

        assertEquals(
            listOf(
                "$BASE_URL/chain-id/4663/network.dat",
                "$BASE_URL/chain-id/4663/token-$address.dat",
                "$BASE_URL/chain-id/4663/display-format/$address-a9059cbb.dat",
            ),
            requestedUrls,
        )
        assertArrayEquals(byteArrayOf(1, 2), result.encodedNetwork)
        assertNull(result.encodedToken)
        assertArrayEquals(byteArrayOf(3, 4), result.encodedDisplayFormat)
        client.close()
    }

    @Test
    fun getDefinitions_malformedContractAndSelector_fetchesOnlyNetwork() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = client { url ->
            requestedUrls += url
            respond(byteArrayOf(1))
        }
        val provider = TrezorEthereumDefinitionsProviderImpl(client)

        val result = provider.getDefinitions(
            TrezorEthereumDefinitionRequest(4663, "../not-an-address", "A9059CBB"),
        )

        assertEquals(listOf("$BASE_URL/chain-id/4663/network.dat"), requestedUrls)
        assertArrayEquals(byteArrayOf(1), result.encodedNetwork)
        assertNull(result.encodedToken)
        assertNull(result.encodedDisplayFormat)
        client.close()
    }

    @Test
    fun getDefinitions_invalidSelector_fetchesNetworkAndTokenOnly() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = client { url ->
            requestedUrls += url
            respond(byteArrayOf(1))
        }
        val provider = TrezorEthereumDefinitionsProviderImpl(client)
        val address = "ab".repeat(20)

        val result = provider.getDefinitions(
            TrezorEthereumDefinitionRequest(4663, address, "A9059CBB"),
        )

        assertEquals(
            listOf(
                "$BASE_URL/chain-id/4663/network.dat",
                "$BASE_URL/chain-id/4663/token-$address.dat",
            ),
            requestedUrls,
        )
        assertArrayEquals(byteArrayOf(1), result.encodedNetwork)
        assertArrayEquals(byteArrayOf(1), result.encodedToken)
        assertNull(result.encodedDisplayFormat)
        client.close()
    }

    @Test
    fun getDefinitions_serverError_returnsEmptyDefinitions() = runTest {
        val client = client { respond(ByteArray(0), HttpStatusCode.InternalServerError) }
        val provider = TrezorEthereumDefinitionsProviderImpl(client)

        val result = provider.getDefinitions(TrezorEthereumDefinitionRequest(4663, null, null))

        assertNull(result.encodedNetwork)
        assertNull(result.encodedToken)
        assertNull(result.encodedDisplayFormat)
        client.close()
    }

    @Test
    fun getDefinitions_requestTimeout_returnsEmptyDefinitions() = runTest {
        val client = client { url -> throw HttpRequestTimeoutException(url, 5_000) }
        val provider = TrezorEthereumDefinitionsProviderImpl(client)

        val result = provider.getDefinitions(TrezorEthereumDefinitionRequest(4663, null, null))

        assertNull(result.encodedNetwork)
        assertNull(result.encodedToken)
        assertNull(result.encodedDisplayFormat)
        client.close()
    }

    @Test
    fun getDefinitions_parentCancellation_propagates() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val neverComplete = CompletableDeferred<Unit>()
        val client = client {
            requestStarted.complete(Unit)
            neverComplete.await()
            respond(ByteArray(0))
        }
        val provider = TrezorEthereumDefinitionsProviderImpl(client)
        val result = async { provider.getDefinitions(TrezorEthereumDefinitionRequest(4663, null, null)) }
        requestStarted.await()

        result.cancel()

        var caught: CancellationException? = null
        try {
            result.await()
        } catch (e: CancellationException) {
            caught = e
        }
        assertTrue(caught != null)
        client.close()
    }

    private fun client(
        handler: suspend MockRequestHandleScope.(String) -> HttpResponseData,
    ): HttpClient {
        val engine = MockEngine { request -> handler(request.url.toString()) }
        return HttpClient(engine) { install(HttpTimeout) }
    }

    private companion object {
        const val BASE_URL = "https://data.trezor.io/firmware/eth-definitions"
    }
}
