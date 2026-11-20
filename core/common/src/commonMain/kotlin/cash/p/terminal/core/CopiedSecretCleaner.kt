package cash.p.terminal.core

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

const val COPIED_SECRET_LIFETIME_MILLIS = 60_000L

sealed interface CopiedSecretState {
    /** The app may not look at the clipboard right now (Android: its window has no focus). */
    data object Unreadable : CopiedSecretState
    data object NotOurs : CopiedSecretState
    data class Pending(val remainingMillis: Long) : CopiedSecretState
}

interface CopiedSecretClipboard {
    fun copiedSecretState(): CopiedSecretState
    fun clear()
}

/**
 * Clears a copied secret once its lifetime runs out, but only while the clipboard still holds it.
 * [scope] must run on the thread that copies, so a copy never lands between the check and the clear.
 */
class CopiedSecretCleaner(private val clipboard: CopiedSecretClipboard, scope: CoroutineScope) {
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { wakeUps.receiveAsFlow().collectLatest { clearWhenExpired() } }
    }

    /** Call after copying a secret and whenever the clipboard may have become readable again. */
    fun check() {
        wakeUps.trySend(Unit)
    }

    private suspend fun clearWhenExpired() {
        while (true) {
            val state = try {
                clipboard.copiedSecretState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Could not read the clipboard" }
                delay(FAILURE_RETRY_MILLIS)
                continue
            }
            when (state) {
                // Unreadable: the next check() picks it up once the app may look again.
                CopiedSecretState.NotOurs, CopiedSecretState.Unreadable -> return
                is CopiedSecretState.Pending -> {
                    if (state.remainingMillis > 0) {
                        delay(state.remainingMillis)
                    } else if (tryClear()) {
                        return
                    } else {
                        delay(FAILURE_RETRY_MILLIS)
                    }
                }
            }
        }
    }

    private fun tryClear(): Boolean = try {
        clipboard.clear()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Could not clear the copied secret" }
        false
    }

    private companion object {
        const val FAILURE_RETRY_MILLIS = 5_000L
        val logger = Logger.withTag("CopiedSecretCleaner")
    }
}
