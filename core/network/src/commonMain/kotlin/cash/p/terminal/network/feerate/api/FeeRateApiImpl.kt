package cash.p.terminal.network.feerate.api

import cash.p.terminal.network.feerate.data.BlockCypherChain
import cash.p.terminal.network.feerate.data.BlockstreamEstimates
import cash.p.terminal.network.feerate.data.MempoolRecommendedFees
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil

private const val MEMPOOL_URL = "https://mempool.space/api/v1/fees/recommended"
private const val BLOCKSTREAM_URL = "https://blockstream.info/api/fee-estimates"
private const val BLOCKCYPHER_DASH_URL = "https://api.blockcypher.com/v1/dash/main"

private const val DASH_TIMEOUT_MS = 10_000L
private const val DASH_FALLBACK_FEE_RATE = 10

private const val DIRECT_IO_TIMEOUT_MS = 5_000L
private const val TOR_IO_TIMEOUT_MS = 30_000L
private const val TOR_REQUEST_TIMEOUT_MS = 60_000L

private val bitcoinLogger = Logger.withTag("FeeRate:Bitcoin")
private val dashLogger = Logger.withTag("FeeRate:Dash")

internal class FeeRateApiImpl(private val httpClient: HttpClient) : FeeRateApi {

    override suspend fun bitcoinFeeRates(torEnabled: Boolean): BitcoinFeeRates = try {
        fetchBitcoinFeeRates(torEnabled)
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        bitcoinLogger.w(e) { "Fee rates request failed" }
        throw e
    }

    override suspend fun dashFeeRate(torEnabled: Boolean): Int = try {
        withTimeout(DASH_TIMEOUT_MS) {
            feePerByte(fetch<BlockCypherChain>(BLOCKCYPHER_DASH_URL, torEnabled).highFeePerKb)
        }
    } catch (e: Exception) {
        // Checked outside withTimeout: our own timeout falls back, the caller's cancellation propagates.
        currentCoroutineContext().ensureActive()
        dashLogger.w(e) { "Fee rate request failed, using fallback" }
        DASH_FALLBACK_FEE_RATE
    }

    private suspend fun fetchBitcoinFeeRates(torEnabled: Boolean): BitcoinFeeRates = if (torEnabled) {
        val estimates = fetch<BlockstreamEstimates>(BLOCKSTREAM_URL, torEnabled)
        BitcoinFeeRates(halfHourFee = ceilFee(estimates.threeBlocks), minimumFee = ceilFee(estimates.eightBlocks))
    } else {
        val fees = fetch<MempoolRecommendedFees>(MEMPOOL_URL, torEnabled)
        BitcoinFeeRates(halfHourFee = fees.halfHourFee, minimumFee = fees.minimumFee)
    }

    // Tor works only because the shared client runs on OkHttp, which honours the system ProxySelector.
    private suspend inline fun <reified T> fetch(url: String, torEnabled: Boolean): T =
        httpClient.get(url) {
            expectSuccess = true
            timeout { applyTimeouts(torEnabled) }
        }.body()
}

private fun HttpTimeoutConfig.applyTimeouts(torEnabled: Boolean) {
    if (torEnabled) {
        connectTimeoutMillis = TOR_IO_TIMEOUT_MS
        socketTimeoutMillis = TOR_IO_TIMEOUT_MS
        requestTimeoutMillis = TOR_REQUEST_TIMEOUT_MS
    } else {
        connectTimeoutMillis = DIRECT_IO_TIMEOUT_MS
        socketTimeoutMillis = DIRECT_IO_TIMEOUT_MS
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
    }
}

private fun ceilFee(value: Double): Int = ceil(value).toInt().coerceAtLeast(1)

private fun feePerByte(feePerKb: Long): Int = ceilFee(feePerKb / 1000.0)
