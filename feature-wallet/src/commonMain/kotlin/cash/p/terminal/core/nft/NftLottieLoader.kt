package cash.p.terminal.core.nft

import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

internal const val MAX_LOTTIE_BYTES = 3 * 1024 * 1024
private const val LOTTIE_PROXY_URL = "https://c.tonapi.io/json"

private val logger = Logger.withTag("NftLottieLoader")

/** Loads an NFT animation through the tonapi proxy, so the creator's host never sees the user. */
class NftLottieLoader(
    private val httpClient: HttpClient,
    private val dispatcherProvider: DispatcherProvider,
) {
    /** Null when the animation is unavailable or larger than [MAX_LOTTIE_BYTES]. */
    suspend fun load(url: String): String? = withContext(dispatcherProvider.io) {
        try {
            httpClient.prepareGet(LOTTIE_PROXY_URL) { parameter("url", url.proxyEncoded()) }.execute { response ->
                val declaredTooLarge = (response.contentLength() ?: 0) > MAX_LOTTIE_BYTES
                if (!response.status.isSuccess() || declaredTooLarge) return@execute null
                val bytes = response.bodyAsChannel().readRemaining(MAX_LOTTIE_BYTES + 1L).readByteArray()
                bytes.takeIf { it.size <= MAX_LOTTIE_BYTES }?.decodeToString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Lottie not loaded: ${e::class.simpleName}" }
            null
        }
    }
}

// The proxy rejects unpadded and standard-alphabet base64.
private fun String.proxyEncoded(): String = Base64.UrlSafe.encode(encodeToByteArray())
