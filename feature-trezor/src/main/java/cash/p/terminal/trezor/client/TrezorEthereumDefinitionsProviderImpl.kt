package cash.p.terminal.trezor.client

import cash.p.terminal.trezorkit.client.TrezorEthereumDefinitionRequest
import cash.p.terminal.trezorkit.client.TrezorEthereumDefinitions
import cash.p.terminal.trezorkit.client.TrezorEthereumDefinitionsProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

internal class TrezorEthereumDefinitionsProviderImpl(
    private val httpClient: HttpClient,
) : TrezorEthereumDefinitionsProvider {

    override suspend fun getDefinitions(request: TrezorEthereumDefinitionRequest): TrezorEthereumDefinitions {
        val chainId = request.chainId.takeIf { it > 0 } ?: return TrezorEthereumDefinitions()
        val contractAddress = request.contractAddress.validatedHex(ADDRESS_HEX_LENGTH)
        val functionSignature = request.functionSignature.validatedHex(FUNCTION_SIGNATURE_HEX_LENGTH)
        val chainPath = "chain-id/$chainId"

        return TrezorEthereumDefinitions(
            encodedNetwork = fetch("$chainPath/network.dat"),
            encodedToken = contractAddress?.let { fetch("$chainPath/token-$it.dat") },
            encodedDisplayFormat = if (contractAddress != null && functionSignature != null) {
                fetch("$chainPath/display-format/$contractAddress-$functionSignature.dat")
            } else {
                null
            },
        )
    }

    private suspend fun fetch(path: String): ByteArray? =
        try {
            val response = httpClient.get("$BASE_URL/$path") {
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }
            if (response.status == HttpStatusCode.OK) response.body<ByteArray>().takeIf(ByteArray::isNotEmpty) else null
        } catch (_: HttpRequestTimeoutException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun String?.validatedHex(expectedLength: Int): String? =
        this?.takeIf { value ->
            value.length == expectedLength && value.all { it in '0'..'9' || it in 'a'..'f' }
        }

    private companion object {
        const val BASE_URL = "https://data.trezor.io/firmware/eth-definitions"
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val ADDRESS_HEX_LENGTH = 40
        const val FUNCTION_SIGNATURE_HEX_LENGTH = 8
    }
}
