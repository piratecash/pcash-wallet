package cash.p.terminal.core.managers

import cash.p.terminal.core.ITokenTransferCostEstimator
import cash.p.terminal.core.TokenTransferCost
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** Token transfer estimates are RPC calls, so a reopened swap screen reuses a recent one. */
class TokenTransferCostCache(
    private val dispatcherProvider: DispatcherProvider,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, TokenTransferCostEstimate>()

    /** A null [key] estimates without storing; null result means the estimate failed. */
    suspend fun get(key: String?, estimator: ITokenTransferCostEstimator): TokenTransferCostEstimate? {
        if (key == null) return estimate(estimator)
        return mutex.withLock {
            entries[key]?.takeIf { it.isFresh }
                ?: estimate(estimator)?.also { entries[key] = it }
        }
    }

    private suspend fun estimate(estimator: ITokenTransferCostEstimator): TokenTransferCostEstimate? =
        withContext(dispatcherProvider.io) {
            try {
                TokenTransferCostEstimate(estimator.estimateTransferCost(), timeSource.markNow())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // EthereumKit's RpcError is a bare Throwable; the message may echo a request header.
                logger.w { "Token transfer estimate failed: ${e::class.simpleName}" }
                null
            }
        }

    private companion object {
        val logger = Logger.withTag("TokenTransferCostCache")
    }
}

class TokenTransferCostEstimate(val cost: TokenTransferCost, private val takenAt: ComparableTimeMark) {
    val isFresh: Boolean
        get() = takenAt.elapsedNow() < TTL

    private companion object {
        val TTL = 10.minutes
    }
}
