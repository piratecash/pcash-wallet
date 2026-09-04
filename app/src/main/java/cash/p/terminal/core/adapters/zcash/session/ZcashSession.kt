package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.adapters.zcash.pools
import cash.p.terminal.core.adapters.zcash.zcashRestartDelayFor
import cash.p.terminal.core.adapters.zcash.zcashErrorName
import cash.p.terminal.core.adapters.zcash.zcashLogger
import cash.p.terminal.wallet.entities.TokenType
import cash.p.zcash.MempoolEvent
import cash.p.zcash.PoolBalance
import cash.p.zcash.PoolSet
import cash.p.zcash.SyncState
import cash.p.zcash.Transaction
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ZcashSessionResult<out T> {
    data class Success<T>(val value: T) : ZcashSessionResult<T>
    data object Unavailable : ZcashSessionResult<Nothing>
}

internal data class ZcashSessionState(
    val syncState: SyncState = SyncState.Stopped,
    val balance: PoolBalance = PoolBalance(emptyMap()),
    val maxSpendable: Map<PoolSet, Long> = emptyMap(),
    val latestHeight: Int = 0,
    internal val minedTransactions: List<Transaction> = emptyList(),
    internal val unconfirmedTransactions: Map<String, Transaction> = emptyMap(),
) {
    val transactions: List<Transaction>
        get() = minedTransactions + unconfirmedTransactions.values
}

/**
 * The only door to a [ZcashWallet]. Every call is counted, because the SDK checks its own closed
 * flag when a call starts and never again — so closing must wait for the calls already inside.
 */
class ZcashSession(
    val accountId: String,
    private val wallet: ZcashWallet,
    val dbAccountId: Int,
    networkPaused: Boolean,
    dispatcherProvider: DispatcherProvider,
) {
    private enum class Phase { ACTIVE, DRAINING, CLOSED }

    private class Operation(val syncGeneration: Int?)

    private data class LocalState(
        val balance: PoolBalance,
        val maxSpendable: Map<PoolSet, Long>,
        val minedTransactions: List<Transaction>,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val gate = Mutex()
    private val syncCancelGate = Mutex()
    private var phase = Phase.ACTIVE
    private var syncGeneration = 0
    private var syncCollector: Job? = null
    private val operations = MutableStateFlow(0)

    /**
     * Owns the whole mempool lifecycle. Start, pause, resume and the drain's stop are one
     * linearized transition, and a replacement job may only start once its predecessor has
     * finished — otherwise a cancelled subscription and its successor overlap on the native side.
     */
    private val mempoolGate = Mutex()
    private var mempoolJob: Job? = null
    private var mempoolPaused = networkPaused
    private val reportedMempoolErrors = mutableSetOf<String>()

    /** Backoff state owned by [ZcashSyncScheduler]; it dies together with the session. */
    var syncAttempts: Int = 0

    /**
     * Sync status and the local snapshot have one publication point. Consumers that react to
     * `Synced` therefore cannot observe the previous balance, history or chain height beside it.
     */
    private val _state = MutableStateFlow(ZcashSessionState())
    internal val state: StateFlow<ZcashSessionState> = _state.asStateFlow()

    init {
        scope.launch { mempoolGate.withLock { startMempool() } }
    }

    suspend fun <T> withOperation(block: suspend (ZcashWallet) -> T): ZcashSessionResult<T> =
        runOperation(block)

    private suspend fun <T> runOperation(
        block: suspend (ZcashWallet) -> T,
    ): ZcashSessionResult<T> {
        if (enter() == null) return ZcashSessionResult.Unavailable
        try {
            return ZcashSessionResult.Success(block(wallet))
        } finally {
            leaveOperation()
        }
    }

    /** Runs one sync pass. Only [ZcashSyncScheduler] calls it — syncs are serialized SDK-wide. */
    suspend fun sync(): ZcashSessionResult<Unit> = supervisorScope {
        var generation = 0
        val collector = async(start = CoroutineStart.LAZY) { collectSync(generation) }
        val operation = enter(collector)
        if (operation == null) {
            collector.cancel()
            return@supervisorScope ZcashSessionResult.Unavailable
        }
        generation = checkNotNull(operation.syncGeneration)
        try {
            collector.start()
            collector.await()
            ZcashSessionResult.Success(Unit)
        } catch (e: CancellationException) {
            if (isCurrentSync(generation)) throw e
            ZcashSessionResult.Success(Unit)
        } finally {
            leaveSync(collector)
        }
    }

    private suspend fun collectSync(generation: Int) {
        try {
            wallet.sync(listOf(dbAccountId)).collect { onSyncState(it, generation) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            publishSyncState(SyncState.Failed(e), generation)
        }
    }

    /** Republishes what the local database already holds; nothing is fetched from the network. */
    suspend fun refresh(): ZcashSessionResult<Unit> = withOperation {
        publishLocalState(readLocalState())
    }

    suspend fun reserveForBroadcast(
        rawTransaction: ByteArray,
        requireOwnInputs: Boolean = true,
    ): ZcashSessionResult<Unit> =
        withOperation { wallet.reserveForBroadcast(dbAccountId, rawTransaction, requireOwnInputs) }

    /** Cancellation is SDK-wide, so it may only be issued while this session is syncing. */
    suspend fun cancelSync() = stopSync(invalidateState = true)

    /**
     * Stops taking work, stops the mempool subscription and waits for the calls already inside.
     * A standalone drain returns to service on timeout; the manager can keep it dormant until it
     * has revalidated that an adapter still owns it.
     */
    suspend fun drain(timeoutMs: Long, reactivateOnTimeout: Boolean = true): Boolean {
        gate.withLock {
            if (phase == Phase.CLOSED) return true
            phase = Phase.DRAINING
            syncGeneration++
            _state.update { it.copy(syncState = SyncState.Stopped) }
        }
        stopSync(invalidateState = false)
        val drained = withTimeoutOrNull(timeoutMs) {
            mempoolGate.withLock { stopMempool() }
            operations.first { it == 0 }
        } != null
        if (!drained && reactivateOnTimeout) reactivate()
        return drained
    }

    /** The subscription is network work, so it follows the same offline switch as the sync. */
    suspend fun pauseMempool() = mempoolGate.withLock {
        mempoolPaused = true
        stopMempool()
    }

    suspend fun resumeMempool() = mempoolGate.withLock {
        mempoolPaused = false
        startMempool()
    }

    /** Valid only once [drain] has reported success. */
    suspend fun close() {
        gate.withLock { phase = Phase.CLOSED }
        wallet.close()
        scope.cancel()
    }

    private suspend fun enter(collector: Job? = null): Operation? = gate.withLock {
        if (phase != Phase.ACTIVE) return@withLock null
        operations.update { it + 1 }
        val generation = if (collector != null) {
            _state.update { it.copy(syncState = SyncState.Connecting) }
            syncCollector = collector
            syncGeneration
        } else {
            null
        }
        Operation(generation)
    }

    private suspend fun stopSync(invalidateState: Boolean) = syncCancelGate.withLock {
        val collector = gate.withLock {
            if (invalidateState) {
                syncGeneration++
                _state.update { it.copy(syncState = SyncState.Stopped) }
            }
            syncCollector
        }
        if (collector != null) {
            collector.cancel()
            withContext(NonCancellable) { wallet.cancelSync() }
        }
    }

    private suspend fun leaveSync(collector: Job) {
        gate.withLock {
            if (syncCollector === collector) syncCollector = null
        }
        leaveOperation()
    }

    private fun leaveOperation() {
        operations.update { it - 1 }
    }

    internal suspend fun reactivate() {
        gate.withLock {
            if (phase != Phase.DRAINING) return
            phase = Phase.ACTIVE
            _state.update { it.copy(syncState = SyncState.Connecting) }
        }
        // The restart waits for the native reader that outlived the timeout; drain() must not wait
        // with it, or the timeout it promised would be void.
        scope.launch { mempoolGate.withLock { startMempool() } }
    }

    private suspend fun onSyncState(state: SyncState, generation: Int) {
        if (!isCurrentSync(generation)) return
        when (state) {
            is SyncState.Syncing -> publishState(generation) {
                it.copy(syncState = state, latestHeight = state.target)
            }

            SyncState.Synced -> {
                val localState = readLocalState()
                publishState(generation) { it.withLocalState(localState).copy(syncState = state) }
            }

            else -> publishState(generation) { it.copy(syncState = state) }
        }
    }

    private suspend fun isCurrentSync(generation: Int): Boolean =
        gate.withLock { syncGeneration == generation }

    private suspend fun publishSyncState(state: SyncState, generation: Int) {
        publishState(generation) { it.copy(syncState = state) }
    }

    private suspend fun publishState(
        generation: Int,
        transform: (ZcashSessionState) -> ZcashSessionState,
    ) {
        gate.withLock {
            if (syncGeneration == generation) _state.update(transform)
        }
    }

    private suspend fun readLocalState() = LocalState(
        balance = wallet.balance(dbAccountId, CONFIRMATIONS),
        maxSpendable = SPENDING_POOL_SETS.associateWith {
            wallet.maxSpendable(dbAccountId, it, CONFIRMATIONS)
        },
        minedTransactions = wallet.transactions(dbAccountId),
    )

    private fun publishLocalState(localState: LocalState) {
        _state.update { it.withLocalState(localState) }
    }

    private fun ZcashSessionState.withLocalState(localState: LocalState) = copy(
        balance = localState.balance,
        maxSpendable = localState.maxSpendable,
        minedTransactions = localState.minedTransactions,
        unconfirmedTransactions = unconfirmedTransactions - localState.minedTransactions.txids(),
    )

    /**
     * Keeps unconfirmed transactions and the chain tip flowing between syncs. It is an ordinary
     * counted operation: the drain is what proves the native reader has actually stopped.
     * A predecessor that is still finishing its cancellation is awaited, never skipped — a
     * cancelled [drain] leaves exactly that state behind and the session must not end up silently
     * unsubscribed. Callers must hold [mempoolGate].
     */
    private suspend fun startMempool() {
        if (mempoolPaused || mempoolJob?.isActive == true) return
        stopMempool()
        mempoolJob = scope.launch {
            var attempt = 0
            while (isActive) {
                val failed = try {
                    val outcome = withOperation { it.mempool().collect { event -> onMempoolEvent(event) } }
                    if (outcome is ZcashSessionResult.Unavailable) return@launch
                    false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val errorName = e.zcashErrorName
                    if (reportedMempoolErrors.add(errorName)) {
                        zcashLogger.e { "Mempool subscription failed error=$errorName" }
                    }
                    true
                }
                attempt = if (failed) attempt + 1 else 0
                delay(zcashRestartDelayFor(attempt, MEMPOOL_RETRY_BASE_MS, MEMPOOL_RETRY_MAX_MS))
            }
        }
    }

    /** Callers must hold [mempoolGate]. */
    private suspend fun stopMempool() {
        mempoolJob?.cancelAndJoin()
        mempoolJob = null
    }

    private fun onMempoolEvent(event: MempoolEvent) {
        when (event) {
            // An epoch only opens a new observation window; it proves nothing about mining, so
            // it must not drop anything from the set.
            is MempoolEvent.Epoch -> _state.update { it.copy(latestHeight = event.height) }
            is MempoolEvent.Unconfirmed -> rememberIncoming(event)
        }
    }

    /**
     * Only incoming transactions: an event about our own spend carries neither the recipient nor
     * the fee, and its memo belongs to the note being spent. Outgoing ones are the pending rows'
     * job.
     */
    private fun rememberIncoming(event: MempoolEvent.Unconfirmed) {
        val received = event.amounts.filter { it.account == dbAccountId }.sumOf { it.value }
        if (received <= 0 || isKnown(event.txid)) return

        val transaction = unconfirmedTransaction(event, received)
        _state.update {
            it.copy(unconfirmedTransactions = it.unconfirmedTransactions + (event.txid to transaction))
        }
        scope.launch {
            delay(UNCONFIRMED_TTL_MS)
            // Only this incarnation expires: a re-announced txid carries its own TTL.
            _state.update { current ->
                if (current.unconfirmedTransactions[event.txid] !== transaction) current
                else current.copy(
                    unconfirmedTransactions = current.unconfirmedTransactions - event.txid
                )
            }
        }
    }

    private fun isKnown(txid: String) = _state.value.let { current ->
        txid in current.unconfirmedTransactions || current.minedTransactions.any { it.txid == txid }
    }

    /**
     * Height `0` is what marks the row unmined for the whole history pipeline, so an unconfirmed
     * transaction needs no separate model and no second mapping.
     */
    private fun unconfirmedTransaction(event: MempoolEvent.Unconfirmed, received: Long) = Transaction(
        id = 0,
        txid = event.txid,
        height = 0,
        time = System.currentTimeMillis() / 1000,
        value = received,
        memo = event.notes.firstOrNull { it.account == dbAccountId && it.memo != null }?.memo,
        fee = 0,
        totalReceived = received,
        isChange = false,
        recipient = null,
    )

    private fun List<Transaction>.txids() = mapTo(mutableSetOf()) { it.txid }

    private companion object {
        const val CONFIRMATIONS = 10

        /** One per address spec, so a spec added later cannot be left without a maximum. */
        val SPENDING_POOL_SETS: List<PoolSet> =
            TokenType.AddressSpecType.entries.map { it.pools() }.distinct()
        const val MEMPOOL_RETRY_BASE_MS = 5_000L
        const val MEMPOOL_RETRY_MAX_MS = 60_000L
        // Chain expiry is 40 blocks — about 50 minutes — after which the transaction can no
        // longer be mined at all.
        const val UNCONFIRMED_TTL_MS = 60 * 60 * 1000L
    }
}
