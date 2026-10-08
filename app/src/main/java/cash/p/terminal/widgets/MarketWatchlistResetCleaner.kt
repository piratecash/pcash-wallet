package cash.p.terminal.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException

/** Resets the market watchlist: the stored favorites and the widgets rendering them. */
class MarketWatchlistResetCleaner(
    private val context: Context,
    private val glanceManager: GlanceAppWidgetManager,
    private val marketFavoritesManager: MarketFavoritesManager,
) {
    private val logger = Logger.withTag("MarketWatchlistResetCleaner")

    // Best-effort like the rest of the post-purge reset: a failure here must not skip the later file cleanup.
    suspend fun clear() {
        try {
            marketFavoritesManager.clear()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.w(error) { "Failed clearing market favorites" }
        }
        clearWidgetState()
    }

    private suspend fun clearWidgetState() {
        runCatching {
            val glanceIds = runCatching { glanceManager.getGlanceIds(MarketWidget::class.java) }
                .getOrElse { emptyList() }

            glanceIds.forEach { glanceId ->
                runCatching {
                    val file = MarketWidgetStateDefinition.getLocation(context, glanceId.toString())
                    if (file.exists()) {
                        file.delete()
                    }
                }.onFailure { logger.w(it) { "Failed deleting widget state for $glanceId" } }
            }

            MarketWidgetWorker.cancel(context)
        }.onFailure { logger.w(it) { "Failed clearing widget state" } }
    }
}
