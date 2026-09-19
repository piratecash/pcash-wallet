package cash.p.terminal.core.adapters

import cash.p.beam.BeamBalance
import cash.p.beam.BeamFailure
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamRestorePhase
import cash.p.beam.BeamRestoreProgress
import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.R
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.TransactionExplorerData
import cash.p.terminal.core.managers.BeamLifecycleCoordinator
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecordConverter
import cash.p.terminal.modules.send.beam.BeamAmount
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlowable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal

class BeamAdapter(
    private val owner: BeamSessionOwner,
    private val session: BeamSessionOwner.Session,
    private val dispatcherProvider: DispatcherProvider,
    private val wallet: Wallet,
    lifecycleCoordinator: BeamLifecycleCoordinator? = null,
    private val sendCoordinator: BeamSendCoordinator? = null,
) : IAdapter, IBalanceAdapter, IReceiveAdapter, ITransactionsAdapter {
    private val monitor = Any()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(dispatcherProvider.io + job)
    // Pause acknowledgements must survive a subsequent resume request.
    private val networkRequests = Channel<NetworkRequest>(Channel.UNLIMITED)
    private val reconciliationRequests = Channel<Unit>(Channel.CONFLATED)
    private var reconciliationJob: Job? = null
    private var latestNetworkRequest: NetworkRequest? = null
    private var networkPaused = session.wallet.state.value.let {
        it == BeamWalletState.Stopped || it == BeamWalletState.Closed
    }
    private var attached = false
    private var stopped = false
    private val balance = MutableStateFlow(session.wallet.balance.value.takeIf { it.isLoaded }?.toBalanceData())
    private val authoritativeBalance =
        MutableStateFlow(session.wallet.balance.value.takeIf { it.isAuthoritative }?.toBalanceData())
    private val state = MutableStateFlow(session.wallet.state.value.toAdapterState())
    private var startupFailure: AdapterState.NotSynced? = null
    private val blockInfo = MutableStateFlow(session.wallet.state.value.toLastBlockInfo())
    private val transactions = MutableStateFlow(session.wallet.transactions.value)
    private val transactionConverter = BeamTransactionRecordConverter(wallet.transactionSource, wallet.token)

    // Omitting policy dependencies supports local-only readers; networking stays disabled.
    private val lifecycle = lifecycleCoordinator?.bind(session.accountId, scope) { enabled ->
        val completion = CompletableDeferred<Unit>(job)
        requestNetwork(NetworkRequest(enabled, completion))
        completion
    }

    private val publicOfflineAddress = session.receiveAddress
    override val receiveAddress: String = publicOfflineAddress.token
    override val isMainNet = publicOfflineAddress.network == BeamNetwork.Mainnet
    val isNetworkPaused: Boolean get() = synchronized(monitor) { networkPaused }
    // The amounts the wallet database reported, authoritative or not: what the balance row shows.
    // Null only in the window before the database has been read at all.
    val lastKnownBalanceData: BalanceData? get() = balance.value
    override val balanceData: BalanceData get() = lastKnownBalanceData ?: BalanceData(BigDecimal.ZERO)

    // Display may lag behind the chain; spending may not.
    // Only a fully synced balance may fund a send.
    val spendableBalanceData: BalanceData? get() = authoritativeBalance.value
    override val maxSpendableBalance: BigDecimal get() = spendableBalanceData?.available ?: BigDecimal.ZERO
    override val balanceState: AdapterState get() = state.value
    override val balanceUpdatedFlow = balance.map { }
    override val balanceStateUpdatedFlow = state.map { }
    override val debugInfo = ""
    override val statusInfo: Map<String, Any> get() = mapOf("Sync State" to balanceState.toString())
    override val transactionsState: AdapterState get() = state.value
    override val transactionsStateUpdatedFlowable get() = state.map { }.asFlowable(dispatcherProvider.io)
    override val lastBlockInfo: LastBlockInfo? get() = blockInfo.value
    override val lastBlockUpdatedFlowable get() = blockInfo.map { }.asFlowable(dispatcherProvider.io)
    // The explorer host doubles as the button title, so a testnet wallet never offers a
    // mainnet link.
    override val explorerTitle = if (isMainNet) MAINNET_EXPLORER_HOST else TESTNET_EXPLORER_HOST

    // Wallet-local transaction IDs are not public explorer transaction hashes.
    override fun getTransactionUrl(transactionHash: String) = ""

    // The kernel ID is the only BEAM identifier the explorer can resolve, but holding one is not
    // enough: an outgoing transaction gets its kernel ID at signing, before the kernel is anywhere
    // near a block. The proof height is what says the chain has it, so both are required and a
    // transaction that is still registering, or that failed on the way, offers no dead button.
    // "Signed offline" items are keyed by the main kernel ID; like other chains' offline items, their
    // link is offered before the chain confirms them.
    override fun getTransactionExplorerData(record: TransactionRecord): List<TransactionExplorerData> {
        val kernelId = when (record) {
            is BeamTransactionRecord -> record.kernelId?.takeIf { it.isNotBlank() && record.blockHeight != null }
            is PendingTransactionRecord -> record.transactionHash.takeIf(::isKernelId)
            else -> null
        } ?: return emptyList()
        return listOf(TransactionExplorerData(explorerTitle, kernelExplorerUrl(kernelId, isMainNet)))
    }

    override suspend fun getTransactions(
        from: TransactionRecord?,
        token: Token?,
        limit: Int,
        transactionType: FilterTransactionType,
        address: String?,
    ): List<TransactionRecord> {
        if (limit <= 0 || !supports(token, transactionType, address)) return emptyList()
        require(from == null || from is BeamTransactionRecord && from.source == wallet.transactionSource)
        return try {
            withContext(dispatcherProvider.io) {
                check(job.isActive) { "BEAM adapter stopped" }
                owner.withSession(session) { sdk ->
                    val records = readTransactions(sdk, from, limit, transactionType)
                    check(job.isActive) { "BEAM adapter stopped" }
                    records.map(transactionConverter::convert)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            error("BEAM transaction read failed")
        }
    }

    private suspend fun readTransactions(
        sdk: BeamWalletSession,
        from: TransactionRecord?,
        limit: Int,
        type: FilterTransactionType,
    ): List<BeamTransaction> {
        val snapshot = sdk.transactions.value
        val result = mutableListOf<BeamTransaction>()
        var offset = 0
        while (offset < snapshot.size) {
            currentCoroutineContext().ensureActive()
            val page = sdk.transactionPage(offset, TRANSACTION_PAGE_SIZE)
            val expected = snapshot.subList(offset, minOf(offset + TRANSACTION_PAGE_SIZE, snapshot.size))
            // Native offsets may shift before the SDK publishes its next snapshot. Fall back to
            // this immutable snapshot instead of joining pages from different history revisions.
            if (page.items != expected) return snapshot.selectTransactions(from, limit, type)
            result += page.items
            val selected = result.selectTransactions(from, limit, type)
            val nextOffset = page.nextOffset ?: return selected
            // Finish the timestamp group: native ordering does not break equal-time ties by ID.
            if (selected.size == limit &&
                page.items.last().createdAtEpochSeconds < selected.last().createdAtEpochSeconds
            ) {
                return selected
            }
            check(nextOffset > offset) { "BEAM transaction page did not advance" }
            offset = nextOffset
        }
        return result.selectTransactions(from, limit, type)
    }

    override fun getTransactionRecordsFlow(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): Flow<List<TransactionRecord>> {
        if (!supports(token, transactionType, address)) return flowOf(emptyList())
        return transactions.map { snapshot ->
            snapshot.selectTransactions(null, Int.MAX_VALUE, transactionType).map(transactionConverter::convert)
        }.flowOn(dispatcherProvider.io)
    }

    private fun supports(token: Token?, type: FilterTransactionType, address: String?) =
        (token == null || token == wallet.token) && address == null &&
            type != FilterTransactionType.Swap && type != FilterTransactionType.Approve

    override fun attachLocalData() = synchronized(monitor) {
        if (attached || stopped) return@synchronized
        attached = true
        scope.launch {
            session.wallet.balance.collect { value ->
                val data = value.toBalanceData()
                publish {
                    // The display keeps the last amounts the database reported; a snapshot that has
                    // read nothing yet must not overwrite them with a zero the wallet never had.
                    if (value.isLoaded) balance.value = data
                    // Dropped for every snapshot that is not authoritative, whether or not it
                    // carries amounts: a wallet that leaves Ready has a balance the chain may since
                    // have invalidated, so nothing may be spent until it is re-confirmed.
                    authoritativeBalance.value = data.takeIf { value.isAuthoritative }
                }
            }
        }
        scope.launch { observeSdkState() }
        scope.launch {
            session.wallet.transactions.collect { value ->
                publish { transactions.value = value }
                requestReconciliation()
            }
        }
        scope.launch { processNetworkRequests() }
        if (sendCoordinator != null) scope.launch { processReconciliationRequests() }
        Unit
    }

    private suspend fun observeSdkState() {
        var wasReady = false
        session.wallet.state.collect { value ->
            publish {
                state.value = if (value == BeamWalletState.Stopped) {
                    startupFailure ?: value.toAdapterState()
                } else value.toAdapterState()
                blockInfo.value = value.toLastBlockInfo()
            }
            val ready = value is BeamWalletState.Ready
            if (ready != wasReady) requestReconciliation()
            wasReady = ready
        }
    }

    private fun canReconcile() = isCurrent && latestNetworkRequest?.enabled == true &&
        session.wallet.state.value is BeamWalletState.Ready

    private fun requestReconciliation() = synchronized(monitor) {
        if (sendCoordinator == null) return@synchronized
        if (canReconcile()) reconciliationRequests.trySend(Unit) else reconciliationJob?.cancel()
        Unit
    }

    private suspend fun processReconciliationRequests() {
        for (request in reconciliationRequests) {
            val runner = synchronized(monitor) {
                if (!canReconcile()) null else scope.launch(start = CoroutineStart.LAZY) {
                    reconcileSends()
                }.also { reconciliationJob = it }
            } ?: continue
            runner.start()
            runner.join()
        }
    }

    private suspend fun reconcileSends() {
        try {
            withCriticalOperation {
                if (synchronized(monitor) { canReconcile() }) {
                    sendCoordinator?.reconcile(session, retryPending = true)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Leave SDK recovery for the next Ready/history trigger; SDK errors may contain secrets.
            Timber.w("BEAM send reconciliation deferred")
        }
    }

    private suspend fun processNetworkRequests() = coroutineScope {
        var networkJob: Job? = null
        for (request in networkRequests) {
            networkJob?.cancelAndJoin()
            networkJob = if (request.enabled) {
                launch { runNetwork(true, request.completion) }.also { runner ->
                    // A queued start can be cancelled before its body/finally ever runs.
                    runner.invokeOnCompletion { request.completion?.cancel() }
                }
            } else {
                runNetwork(false)
                synchronized(monitor) {
                    if (job.isActive && latestNetworkRequest === request && owner.current === session) {
                        networkPaused = true
                    }
                }
                request.completion?.complete(Unit)
                null
            }
        }
    }

    override fun resumeNetwork() {
        attachLocalData()
        lifecycle?.resume()
    }

    override fun pauseNetwork() {
        pauseRequest()
    }

    private fun pauseRequest() = lifecycle?.pause() ?: CompletableDeferred<Unit>(job).also {
        requestNetwork(NetworkRequest(enabled = false, completion = it))
    }

    suspend fun pauseNetworkAndAwait() {
        val completion = pauseRequest()
        try {
            completion.await()
        } catch (_: CancellationException) {
            // Adapter shutdown cancels the acknowledgement independently of its caller.
            currentCoroutineContext().ensureActive()
            error("BEAM network pause failed")
        }
        check(isNetworkPaused) { "BEAM network pause was superseded" }
    }

    private fun requestNetwork(request: NetworkRequest) = synchronized(monitor) {
        attachLocalData()
        if (stopped || !job.isActive) {
            request.completion?.completeExceptionally(IllegalStateException("BEAM adapter stopped"))
        } else {
            latestNetworkRequest = request
            // Do not join here: lease release may await this very network-stop acknowledgement.
            if (!request.enabled) reconciliationJob?.cancel()
            networkPaused = false
            networkRequests.trySend(request)
        }
        Unit
    }

    override suspend fun refresh() {
        attachLocalData()
        try {
            lifecycle?.retry { balanceState is AdapterState.NotSynced }
        } catch (_: CancellationException) {
            // A failed or superseded network request cancels its acknowledgement, not the caller.
            currentCoroutineContext().ensureActive()
        }
    }

    suspend fun <T> withCriticalOperation(block: suspend () -> T): T {
        val policy = checkNotNull(lifecycle) { "BEAM lifecycle unavailable" }
        attachLocalData()
        return policy.withLease(BeamLifecycleCoordinator.Lease.CriticalOperation) {
            check(isCurrent && policy.canRun) { "BEAM network unavailable" }
            block()
        }
    }

    suspend fun pollTransactions(): List<TransactionRecord> {
        val policy = lifecycle ?: return emptyList()
        if (!isCurrent || session.wallet.state.value is BeamWalletState.Restoring) return emptyList()
        attachLocalData()
        return policy.withLease(BeamLifecycleCoordinator.Lease.Polling) {
            if (!policy.canRun) return@withLease emptyList()
            val synced = session.wallet.state.first {
                it is BeamWalletState.Ready || it is BeamWalletState.Restoring || it == BeamWalletState.Closed
            }
            if (!isCurrent || !policy.canRun || synced !is BeamWalletState.Ready) return@withLease emptyList()
            getTransactions(null, null, 100, FilterTransactionType.All, null)
        }
    }

    val accountId: String get() = session.accountId
    private val isCurrent: Boolean get() = job.isActive && owner.current === session

    override fun stop() {
        lifecycle?.stop()
        synchronized(monitor) {
            stopped = true
            networkRequests.close()
            reconciliationRequests.close()
            job.cancel()
        }
    }

    // AdapterFactory serializes this identity check and close with all BEAM acquisitions.
    internal suspend fun close() {
        stop()
        job.cancelAndJoin()
        lifecycle?.awaitOperations()
        if (owner.current === session) {
            try {
                owner.close()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                error("BEAM wallet close failed")
            }
        }
    }

    private suspend fun runNetwork(enabled: Boolean, completion: CompletableDeferred<Unit>? = null) {
        try {
            if (enabled) {
                if (lifecycle?.canRun != true || !isCurrent) return
                publish { startupFailure = null }
                owner.start(session)
                completion?.complete(Unit)
                requestReconciliation()
                awaitCancellation()
            } else {
                owner.stop(session)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            publish {
                val failure = error.toSafeAdapterState()
                if (enabled) startupFailure = failure
                state.value = failure
            }
            if (!enabled) job.cancel()
        } finally {
            completion?.cancel()
            if (enabled && owner.current === session) stopNetwork()
        }
    }

    private suspend fun stopNetwork() {
        try {
            owner.stop(session)
            synchronized(monitor) { if (stopped) networkPaused = true }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            publish { state.value = unavailable(Translator.getString(R.string.beam_wallet_stop_failed)) }
            job.cancel()
        }
    }

    private fun publish(update: () -> Unit) = synchronized(monitor) {
        if (!stopped && job.isActive && owner.current === session) update()
    }

    private class NetworkRequest(val enabled: Boolean, val completion: CompletableDeferred<Unit>? = null)

    companion object {
        private const val MAINNET_EXPLORER_HOST = "explorer.beam.mw"
        private const val TESTNET_EXPLORER_HOST = "testnet.explorer.beam.mw"
        private const val KERNEL_ID_HEX_LENGTH = 64

        fun kernelExplorerUrl(kernelId: String, mainNet: Boolean): String {
            val host = if (mainNet) MAINNET_EXPLORER_HOST else TESTNET_EXPLORER_HOST
            return "https://$host/block?kernel_id=$kernelId"
        }

        private fun isKernelId(value: String) =
            value.length == KERNEL_ID_HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

private fun BeamBalance.toBalanceData() = BalanceData(
    // Available already includes shielded funds; sending describes outgoing funds.
    available = BigDecimal.valueOf(available, BeamAmount.DECIMALS),
    timeLocked = BigDecimal.valueOf(maturing, BeamAmount.DECIMALS),
    pending = BigDecimal.valueOf(receiving, BeamAmount.DECIMALS),
)

private fun BeamWalletState.toAdapterState(): AdapterState = when (this) {
    BeamWalletState.Connecting -> AdapterState.Connecting
    is BeamWalletState.Ready -> AdapterState.Synced
    is BeamWalletState.Syncing -> syncing(currentHeight, targetHeight, syncDone, syncTotal)
    is BeamWalletState.Restoring -> progress.toAdapterState()
    BeamWalletState.Closed -> unavailable(Translator.getString(R.string.beam_wallet_state_closed))
    BeamWalletState.Stopped -> unavailable(Translator.getString(R.string.beam_wallet_state_stopped))
    is BeamWalletState.Offline -> unavailable(Translator.getString(R.string.beam_wallet_state_offline))
    is BeamWalletState.Error -> failure.toSafeAdapterState()
}

private fun Exception.toSafeAdapterState() = unavailable(
    if (this is BeamFailure.Node || this is BeamFailure.Download && retryable) {
        Translator.getString(R.string.beam_sync_network_error)
    } else {
        Translator.getString(R.string.beam_wallet_operation_failed)
    }
)

private fun BeamRestoreProgress.toAdapterState() = AdapterState.Syncing(
    progress = when (phase) {
        BeamRestorePhase.DownloadingSnapshot -> percentage(downloadedBytes, totalBytes)
        else -> percentage(currentHeight, targetHeight)
    },
    substatus = AdapterState.Substatus.SnapshotRestore(
        stage = phase.toAdapterStage(),
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
    ),
)

private fun BeamRestorePhase.toAdapterStage() = when (this) {
    BeamRestorePhase.ResolvingBirthday -> AdapterState.SnapshotRestoreStage.ResolvingBirthday
    BeamRestorePhase.DownloadingSnapshot -> AdapterState.SnapshotRestoreStage.DownloadingSnapshot
    BeamRestorePhase.ValidatingSnapshot -> AdapterState.SnapshotRestoreStage.ValidatingSnapshot
    BeamRestorePhase.CountingShieldedOutputs -> AdapterState.SnapshotRestoreStage.CountingShieldedOutputs
    BeamRestorePhase.ScanningWalletOutputs -> AdapterState.SnapshotRestoreStage.ScanningWalletOutputs
    BeamRestorePhase.ImportingSnapshot -> AdapterState.SnapshotRestoreStage.ImportingSnapshot
    BeamRestorePhase.CatchingUp -> AdapterState.SnapshotRestoreStage.CatchingUp
}

private fun syncing(current: Long?, target: Long?, syncDone: Long?, syncTotal: Long?): AdapterState.Syncing {
    // The node's own counters describe the work left to do; the height difference only describes
    // how far behind the tip is, which stalls at a constant value while a block pack is verified.
    val nodeProgress = percentage(syncDone, syncTotal)
    if (nodeProgress != null) {
        // A null backlog is what routes the status text to the percentage wording instead of
        // "N blocks left"; the text checks blocksRemained first.
        return AdapterState.Syncing(progress = nodeProgress, blocksRemained = null)
    }
    // An older native library reports no counters: keep exactly today's behaviour.
    return AdapterState.Syncing(
        progress = percentage(current, target),
        blocksRemained = blocksLeft(current, target),
    )
}

private fun blocksLeft(current: Long?, target: Long?): Long? {
    if (current == null || target == null) return null
    return if (current >= 0 && target > 0) (target - current).coerceAtLeast(0) else null
}

private fun percentage(current: Long?, total: Long?): Double? {
    if (current == null || total == null) return null
    return if (current >= 0 && total > 0) (current.toDouble() / total * 100).coerceIn(0.0, 100.0) else null
}

private fun unavailable(message: String) = AdapterState.NotSynced(IllegalStateException(message))

private const val TRANSACTION_PAGE_SIZE = 200

private fun List<BeamTransaction>.selectTransactions(
    from: TransactionRecord?,
    limit: Int,
    type: FilterTransactionType,
) = asSequence()
    .filter { it.matches(type) && it.isAfter(from) }
    .sortedWith(compareByDescending<BeamTransaction> { it.createdAtEpochSeconds }.thenByDescending { it.id })
    .take(limit)
    .toList()

private fun BeamTransaction.matches(type: FilterTransactionType) = when (type) {
    FilterTransactionType.All -> true
    FilterTransactionType.Incoming -> direction == BeamTransactionDirection.Incoming
    FilterTransactionType.Outgoing -> direction != BeamTransactionDirection.Incoming
    FilterTransactionType.Swap, FilterTransactionType.Approve -> false
}

private fun BeamTransaction.isAfter(from: TransactionRecord?) = from == null ||
    createdAtEpochSeconds < from.timestamp || createdAtEpochSeconds == from.timestamp && id < from.transactionHash

private fun BeamWalletState.toLastBlockInfo(): LastBlockInfo? {
    val height = when (this) {
        is BeamWalletState.Ready -> height
        is BeamWalletState.Syncing -> currentHeight
        is BeamWalletState.Restoring -> progress.currentHeight
        is BeamWalletState.Offline -> lastKnownHeight
        else -> null
    }
    return height?.takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.let { LastBlockInfo(it.toInt()) }
}
