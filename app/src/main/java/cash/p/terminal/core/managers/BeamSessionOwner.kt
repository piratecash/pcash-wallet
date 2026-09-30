package cash.p.terminal.core.managers

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamWalletSession
import cash.p.terminal.wallet.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

// Share one owner in AdapterManager; use start for startup and withSession for other SDK access.
class BeamSessionOwner(private val factory: BeamSessionFactory) {
    private val mutex = Mutex()
    private val monitor = Any()
    private var requested: Pair<String, BeamNetwork>? = null
    private var generation = 0L
    private var active: Session? = null
    private var cleanupFailure: Throwable? = null

    val current: Session?
        get() = synchronized(monitor) { active?.takeIf { it.generation == generation } }

    suspend fun acquire(account: Account, network: BeamNetwork = BeamNetwork.Mainnet): Session {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        factory.ensureAvailable(account.id)
        val request = select(account.id to network)
        // Once selected, even a cancelled waiter must finish retiring the previous account.
        return withContext(NonCancellable) {
            mutex.withLock {
                checkRequest(request)
                factory.ensureAvailable(account.id)
                current?.let {
                    caller.ensureActive()
                    return@withLock it
                }
                retire()
                caller.ensureActive()
                open(account, network, request, caller)
            }
        }
    }

    suspend fun close() {
        val request = select(null)
        withContext(NonCancellable) {
            mutex.withLock {
                if (isRequested(request)) retire()
            }
        }
    }

    internal fun fenceDeletion(accountIds: List<String>) = synchronized(monitor) {
        if (requested?.first in accountIds) {
            requested = null
            generation++
        }
    }

    internal suspend fun drainDeleted(accountIds: List<String>) = withContext(NonCancellable) {
        mutex.withLock {
            cleanupFailure?.let { throw IllegalStateException("BEAM session cleanup failed", it) }
            if (active?.accountId in accountIds) retire()
        }
    }

    // Startup may await a snapshot download. Only registration holds the handoff mutex, so retire
    // can cancel and join startup before touching the native wallet. Concurrent calls share startup.
    // Cancelling the initiating caller also cancels startup; stop before retrying it.
    suspend fun start(owner: Session): Unit = coroutineScope {
        val startup = mutex.withLock {
            factory.ensureAvailable(owner.accountId)
            check(current === owner) { "BEAM session owner is no longer current" }
            owner.startup ?: async(start = CoroutineStart.LAZY) {
                owner.wallet.start()
            }.also { owner.startup = it }
        }
        startup.await()
        currentCoroutineContext().ensureActive()
        checkRequest(owner.generation)
    }

    // Keep stop awaitable: a later start must not overlap the SDK's cancellation cleanup.
    suspend fun stop(owner: Session) = withContext(NonCancellable) {
        mutex.withLock {
            check(current === owner) { "BEAM session owner is no longer current" }
            owner.startup?.cancelAndJoin()
            owner.wallet.stop()
            owner.startup = null
        }
    }

    // Do not call SDK start/stop here: use start/stop(owner) for coordinated handoff.
    // The block must not retain the SDK session or launch work that outlives the block.
    suspend fun <T> withSession(owner: Session, block: suspend (BeamWalletSession) -> T): T =
        mutex.withLock {
            check(current === owner) { "BEAM session owner is no longer current" }
            factory.ensureAvailable(owner.accountId)
            val result = block(owner.wallet)
            currentCoroutineContext().ensureActive()
            checkRequest(owner.generation)
            result
        }

    private suspend fun open(
        account: Account,
        network: BeamNetwork,
        request: Long,
        caller: CoroutineContext,
    ): Session {
        var wallet: BeamWalletSession? = null
        try {
            checkRequest(request)
            val opened = factory.open(account, network).also { wallet = it }
            factory.ensureAvailable(account.id)
            val address = opened.receiveAddress(BeamAddressType.PublicOffline)
            val owner = Session(account.id, network, opened, address, request)
            synchronized(monitor) {
                caller.ensureActive()
                checkRequest(request)
                active = owner
            }
            return owner
        } catch (error: Throwable) {
            wallet?.let { closeWallet(it) }
            throw error
        }
    }

    private fun select(scope: Pair<String, BeamNetwork>?): Long = synchronized(monitor) {
        if (requested != scope) {
            requested = scope
            generation++
        }
        generation
    }

    private fun isRequested(request: Long) = synchronized(monitor) { generation == request }

    private fun checkRequest(request: Long) {
        if (!isRequested(request)) throw CancellationException("BEAM account request was superseded")
    }

    private suspend fun retire() {
        cleanupFailure?.let { throw IllegalStateException("BEAM session cleanup failed", it) }
        val previous = synchronized(monitor) { active.also { active = null } }
        previous?.let {
            it.startup?.cancelAndJoin()
            closeWallet(it.wallet)
        }
    }

    private suspend fun closeWallet(wallet: BeamWalletSession) = withContext(NonCancellable) {
        try {
            try {
                wallet.stop()
            } finally {
                wallet.close()
            }
        } catch (error: Throwable) {
            // Opening another native wallet is unsafe when shutdown did not complete successfully.
            cleanupFailure = error
            throw error
        }
    }

    inner class Session internal constructor(
        val accountId: String,
        val network: BeamNetwork,
        internal val wallet: BeamWalletSession,
        private val address: BeamAddress,
        internal val generation: Long,
    ) {
        internal var startup: Deferred<Unit>? = null

        val receiveAddress: BeamAddress
            get() = synchronized(monitor) {
                check(current === this) { "BEAM session owner is no longer current" }
                address
            }
    }
}
