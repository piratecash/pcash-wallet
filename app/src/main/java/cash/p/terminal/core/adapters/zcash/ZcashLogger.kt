package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.managers.NetworkErrorInfo
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.warningSanitized
import cash.p.terminal.core.tryOrNull
import cash.p.zcash.FailureCategory
import cash.p.zcash.SyncStage
import cash.p.zcash.SyncState
import cash.p.zcash.ZcashException
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import java.net.URI
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal val zcashLogger = Logger.withTag("ZEC")

/** The tag contains "zcash", which is what the blockchain status screen filters the App Log by. */
internal const val ZCASH_KIT_LOG_TAG = "zcash-kit"

internal fun zcashAppLogger(accountId: String): AppLogger = AppLogger(ZCASH_KIT_LOG_TAG).getScoped(accountId)

internal val Throwable.zcashErrorName: String
    get() = this::class.simpleName ?: "Unknown"

private val NETWORK_CATEGORIES = setOf(FailureCategory.TLS, FailureCategory.TIMEOUT, FailureCategory.NETWORK)
private val NETWORK_CLASS_MARKERS = listOf("IO", "Socket", "Connect")

/** The target height is a pure network call, so a failure there is the server's whatever the message says. */
internal fun Throwable.isZcashNetworkFailure(): Boolean = when (this) {
    is ZcashException -> category in NETWORK_CATEGORIES ||
        (stage == SyncStage.TARGET && category != FailureCategory.DATABASE)

    else -> NETWORK_CLASS_MARKERS.any { it in this::class.simpleName.orEmpty() }
}

/** Where a session reports its sync lifecycle and failures: the App Log and the network error tracker. */
internal class ZcashSessionDiagnostics(
    val logger: AppLogger,
    private val networkErrorTracker: NetworkErrorTracker,
    private val accountId: String,
    val serverUrl: String,
) {
    fun syncFailed(error: Throwable) {
        val zcashError = error as? ZcashException
        val stage = zcashError?.stage?.name ?: UNKNOWN
        logger.warningSanitized("sync failed stage=$stage category=${zcashError?.category?.name ?: UNKNOWN}", error)
        if (!error.isZcashNetworkFailure()) return
        networkErrorTracker.record(
            BlockchainType.Zcash,
            accountId,
            NetworkErrorInfo(
                source = "Zcash",
                method = "sync:$stage",
                url = serverUrl,
                host = tryOrNull { URI(serverUrl).host }.orEmpty(),
                resolvedIps = emptyList(),
                throwable = error,
            ),
        )
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }
}

/** One line per 10 % of the initial range or per [PROGRESS_INTERVAL], whichever comes first. */
internal class SyncProgressLog(private val logger: AppLogger) {
    private val started = TimeSource.Monotonic.markNow()
    private var lastLoggedAt = started
    private var firstCurrent: Int? = null
    private var lastTarget = 0
    private var lastLoggedHeight = 0

    fun onSyncing(state: SyncState.Syncing) {
        lastTarget = state.target
        val first = firstCurrent
        if (first == null) {
            firstCurrent = state.current
            log(state, "sync: target=${state.target} current=${state.current}")
            return
        }
        val step = maxOf((state.target - first) / 10, 1)
        if (state.current - lastLoggedHeight >= step || lastLoggedAt.elapsedNow() >= PROGRESS_INTERVAL) {
            log(state, "sync: progress current=${state.current} target=${state.target}")
        }
    }

    fun onSynced() {
        logger.info("sync: done target=$lastTarget elapsed=${started.elapsedNow().inWholeMilliseconds}ms")
    }

    private fun log(state: SyncState.Syncing, message: String) {
        lastLoggedHeight = state.current
        lastLoggedAt = TimeSource.Monotonic.markNow()
        logger.info(message)
    }

    private companion object {
        val PROGRESS_INTERVAL = 30.seconds
    }
}
