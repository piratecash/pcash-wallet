package cash.p.terminal.shared.market

import cash.p.terminal.wallet.MarketKitWrapper
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.AppForegroundState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Syncs the coin catalog on every return to the foreground, as the Android app does. */
class MarketCatalogSyncer(
    private val marketKit: MarketKitWrapper,
    private val foreground: AppForegroundState,
) {
    private val logger = Logger.withTag("MarketCatalogSyncer")

    fun start(scope: CoroutineScope): Job = scope.launch {
        foreground.inForeground.filter { it }.collectLatest { sync() }
    }

    private suspend fun sync() {
        try {
            marketKit.sync(forceUpdate = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Coin catalog sync failed" }
        }
    }
}
