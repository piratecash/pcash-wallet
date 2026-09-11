package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One [ZcashSession] per account, shared by that account's adapters. Opening the same database
 * twice would hand out two handles onto one native pool, so the registry is the only way in.
 */
class ZcashSessionManager(
    private val walletOpener: ZcashWalletOpener,
    private val scheduler: ZcashSyncScheduler,
    private val offlineModeManager: OfflineModeManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    private class Entry(val session: ZcashSession, var refCount: Int)

    private val mutex = Mutex()

    /** Serializes closes so a removed-but-still-draining session is never reported as closed. */
    private val closeGate = Mutex()
    private val sessions = mutableMapOf<String, Entry>()

    /**
     * The entry a close took out of [sessions]. Adapters release asynchronously, so one may let go
     * during the drain: without this it would find nothing, skip its decrement, and the entry the
     * timeout puts back would count a holder that is already gone.
     */
    private var closing: Entry? = null

    /**
     * An opening waits out a close in progress: the closing session is out of [sessions] while it
     * drains, so opening now would put a second native handle on the database it still reads.
     */
    suspend fun acquire(wallet: Wallet): ZcashSession = closeGate.withLock {
        val accountId = wallet.account.id
        val session = reuse(accountId) ?: open(wallet)
        session.reactivate()
        scheduler.enqueue(session)
        session
    }

    private suspend fun reuse(accountId: String): ZcashSession? = mutex.withLock {
        sessions[accountId]?.let { entry ->
            entry.refCount++
            entry.session
        }
    }

    private suspend fun open(wallet: Wallet): ZcashSession {
        val accountId = wallet.account.id
        val opened = walletOpener.open(wallet)
        // Offline mode is a property of the account, not of the adapter that happened to ask
        // first: a session opened while paused must not start any network work at all.
        return ZcashSession(
            accountId = accountId,
            wallet = opened.wallet,
            dbAccountId = opened.dbAccountId,
            networkPaused = offlineModeManager.isNetworkPaused(accountId, BlockchainType.Zcash),
            dispatcherProvider = dispatcherProvider,
        ).also { session -> mutex.withLock { sessions[accountId] = Entry(session, 1) } }
    }

    /**
     * Takes the session the caller actually holds: an erase drops the entry while adapters still
     * reference it, and their release must not close whatever session replaced it.
     */
    suspend fun release(session: ZcashSession) {
        mutex.withLock {
            val entry = entryOf(session) ?: return
            entry.refCount = (entry.refCount - 1).coerceAtLeast(0)
            if (entry.refCount > 0) return
        }
        withContext(NonCancellable) {
            closeGate.withLock {
                val entry = mutex.withLock {
                    sessions[session.accountId]
                        ?.takeIf { it.session === session && it.refCount == 0 }
                        ?.let(::takeForClose)
                } ?: return@withLock
                close(entry)
            }
        }
    }

    /**
     * Closes the session whoever still holds it — the eraser owns the database next, and an
     * adapter releases asynchronously. False means the drain timed out: nothing was closed and the
     * account is back in service.
     */
    suspend fun closeForErase(accountId: String): Boolean = closeGate.withLock {
        val entry = mutex.withLock { sessions[accountId]?.let(::takeForClose) } ?: return@withLock true
        close(entry)
    }

    /**
     * The move out of [sessions] and into [closing] is one critical section: a release that took
     * [mutex] in between would find the entry in neither and lose its decrement.
     * Callers must hold [mutex] and [closeGate].
     */
    private fun takeForClose(entry: Entry): Entry = entry.also {
        sessions.remove(it.session.accountId)
        closing = it
    }

    /**
     * The entry is put back unchanged on timeout: its reference count belongs to adapters that
     * never let go, and a fresh count would let the next release close the session under them.
     * Callers must hold [closeGate] and must have parked the entry with [takeForClose] — an
     * absent entry is what tells the eraser the session is gone, so it may not be observable
     * while a close is still draining.
     */
    private suspend fun close(entry: Entry): Boolean = withContext(NonCancellable) {
        val session = entry.session
        try {
            scheduler.remove(session)
            if (session.drain(DRAIN_TIMEOUT_MS, reactivateOnTimeout = false)) {
                session.close()
                true
            } else {
                mutex.withLock {
                    sessions[session.accountId] = entry
                    if (entry.refCount > 0) {
                        session.reactivate()
                        scheduler.enqueue(session)
                    }
                }
                false
            }
        } finally {
            mutex.withLock { if (closing === entry) closing = null }
        }
    }

    /** Callers must hold [mutex]. */
    private fun entryOf(session: ZcashSession): Entry? =
        (sessions[session.accountId] ?: closing)?.takeIf { it.session === session }

    private companion object {
        const val DRAIN_TIMEOUT_MS = 30_000L
    }
}
