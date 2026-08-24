package cash.p.terminal.core.adapters.zcash

import android.os.SystemClock
import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.SignedOfflineZcashTransaction
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.core.adapters.zcash.session.ZcashSession
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionManager
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionResult
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionState
import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.isZcashAlreadyCommittedToBestChainError
import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.managers.NotBroadcastException
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.core.onPollingStarted
import cash.p.terminal.core.onPollingStopped
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.OneTimeReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Addresses
import cash.p.zcash.Balance
import cash.p.zcash.BroadcastResult
import cash.p.zcash.MigrationEvent
import cash.p.zcash.MigrationPhase
import cash.p.zcash.MigrationStatus
import cash.p.zcash.PaymentOptions
import cash.p.zcash.Pool
import cash.p.zcash.PoolBalance
import cash.p.zcash.PoolSet
import cash.p.zcash.Recipient
import cash.p.zcash.SyncState
import cash.p.zcash.Transaction
import cash.p.zcash.ZcashAddressKind
import cash.p.zcash.ZcashException
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import cash.p.zcash.deriveSpendingKey
import cash.p.zcash.transactionId
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

class ZcashAdapter(
    private val wallet: Wallet,
    private val addressSpecTyped: AddressSpecType?,
    private val backgroundManager: BackgroundManager,
    private val singleUseAddressManager: ZcashSingleUseAddressManager,
    private val sessionManager: ZcashSessionManager,
    private val ironwoodMigrations: ZcashIronwoodMigrationRegistry,
    addressDeriver: ZcashAddressDeriver,
    private val dispatcherProvider: DispatcherProvider,
) : IAdapter, IBalanceAdapter, IReceiveAdapter, ITransactionsAdapter, ISendZcashAdapter,
    OneTimeReceiveAdapter {

    private val zcashKey = wallet.zcashKey() ?: throw UnsupportedAccountException()

    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val transactionsProvider = ZcashTransactionsProvider()
    private val pollingSessionCount = AtomicInteger(0)

    private val backgroundKeepAliveManager: BackgroundKeepAliveManager by inject(
        BackgroundKeepAliveManager::class.java
    )
    private val offlineModeManager: OfflineModeManager by inject(OfflineModeManager::class.java)

    private val adapterStateUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()
    private val lastBlockUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()
    private val balanceUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()

    private val sessionMutex = Mutex()

    @Volatile
    private var session: ZcashSession? = null
    private var bindJob: Job? = null
    private var appliedSessionState: ZcashSessionState? = null
    private var lastDiagLogTimeMs: Long? = null
    private var lastDiagSyncState: String? = null

    @Volatile
    private var stopped = false

    private val ownAddresses: Addresses? = runBlocking {
        tryOrNull { addressDeriver.addresses(zcashKey) }
    }

    override val receiveAddress: String =
        ownAddresses?.let { addressSpecTyped.selectZcashReceiver(it) }.orEmpty()
    override val isMainNet: Boolean = true

    @Volatile
    private var poolBalance: PoolBalance? = null

    @Volatile
    private var latestHeight: Int = 0

    @Volatile
    private var accountBirthday: Int = 0

    /** Kept for the diagnostic line: only [SyncState.Syncing] carries the scan heights. */
    @Volatile
    private var lastSyncing: SyncState.Syncing? = null

    /** The lowest height the current sync pass started from; progress is measured against it. */
    private var syncAnchor: Int? = null

    private var balanceCheckJob: Job? = null

    val poolName: String
        get() = poolLabel(addressSpecTyped)

    private var syncState: AdapterState = AdapterState.Connecting
        set(value) {
            if (value != field) {
                field = value
                adapterStateUpdatedSubject.onNext(Unit)
            }
        }

    private val balance: Balance
        get() = poolBalance?.forSpec(addressSpecTyped) ?: Balance()

    // region lifecycle

    // ISendZcashAdapter also declares `start()` (predating this split), so Kotlin requires an
    // explicit override to resolve the diamond; delegate straight to IAdapter's default composition.
    override fun start() {
        super<IAdapter>.start()
    }

    override fun attachLocalData() {
        scope.launch { acquireSession() }
        subscribeToBackground()
    }

    override fun resumeNetwork() {
        scope.launch {
            acquireSession()
            session?.resumeMempool()
        }
    }

    override fun pauseNetwork() {
        scope.launch { pauseNetworkAndAwait() }
    }

    /** Only the network work stops: the session stays open so balances and history are readable. */
    suspend fun pauseNetworkAndAwait() {
        session?.cancelSync()
        session?.pauseMempool()
    }

    val isNetworkPaused: Boolean
        get() = offlineModeManager.isNetworkPaused(wallet.account.id, BlockchainType.Zcash)

    override fun stop() {
        stopped = true
        scope.launch {
            releaseSession()
            scope.cancel()
        }
    }

    override suspend fun refresh() {
        session?.refresh()
    }

    fun startForPolling() {
        pollingSessionCount.onPollingStarted {
            start()
        }
    }

    fun stopForPolling() {
        pollingSessionCount.onPollingStopped(backgroundManager) {
            scope.launch { releaseSession() }
        }
    }

    private fun subscribeToBackground() = scope.launch {
        backgroundManager.stateFlow.collect { state ->
            when (state) {
                BackgroundManagerState.EnterForeground -> acquireSession()
                BackgroundManagerState.EnterBackground ->
                    if (!hasActiveBackgroundSession()) releaseSession()

                BackgroundManagerState.Unknown,
                BackgroundManagerState.AllActivitiesDestroyed -> Unit
            }
        }
    }

    // ZEC is intentionally kept running in the background during an active polling session or
    // realtime keep-alive, so the session must survive those cases too, not only the foreground.
    private fun hasActiveBackgroundSession(): Boolean =
        pollingSessionCount.get() > 0 || backgroundKeepAliveManager.isKeepAlive(BlockchainType.Zcash)

    private suspend fun acquireSession() {
        sessionMutex.withLock {
            if (stopped || session != null) return
            val acquired = sessionManager.acquire(wallet)
            session = acquired
            bindJob = scope.launch { bind(acquired) }
        }
        // Opening the wallet takes seconds, and a pause that arrived meanwhile found no session to
        // stop, so the offline state is re-read once the session is reachable.
        if (isNetworkPaused) pauseNetworkAndAwait()
    }

    private suspend fun releaseSession() {
        val released = sessionMutex.withLock {
            val current = session ?: return
            session = null
            bindJob?.cancel()
            bindJob = null
            feeGeneration++
            current
        }
        sessionManager.release(released)
    }

    private suspend fun bind(session: ZcashSession) = coroutineScope {
        appliedSessionState = null
        launch { session.state.collect(::onSessionState) }
        launch { recalculateFeeOnChange(session) }

        accountBirthday = walletOrNull { zcash, id ->
            zcash.accounts().firstOrNull { it.id == id }?.birthHeight
        } ?: 0
        // A session may never sync — offline, or an account the scheduler is skipping — so the
        // local database is published once on bind, otherwise every screen stays empty.
        session.refresh()
    }

    // endregion

    // region session access

    private suspend fun <T> withWallet(block: suspend (ZcashWallet, Int) -> T): ZcashSessionResult<T> {
        val current = session ?: return ZcashSessionResult.Unavailable
        return current.withOperation { block(it, current.dbAccountId) }
    }

    private suspend fun <T> requireWallet(block: suspend (ZcashWallet, Int) -> T): T =
        when (val result = withWallet(block)) {
            is ZcashSessionResult.Success -> result.value
            ZcashSessionResult.Unavailable -> error("Zcash wallet session is unavailable")
        }

    private suspend fun <T> walletOrNull(block: suspend (ZcashWallet, Int) -> T): T? =
        (withWallet(block) as? ZcashSessionResult.Success)?.value

    /** The unified full viewing key of this account, or null while the session is unavailable. */
    suspend fun ufvk(): String? = walletOrNull { zcash, id -> zcash.viewingKey(id) }

    private suspend fun <T> withSpendingKey(block: suspend (ByteArray) -> T): T {
        val phrase = zcashKey as? ZcashKey.Phrase
            ?: throw UnsupportedException("Zcash spending requires a mnemonic account")
        // The wallet database was restored without an explicit account index, so index 0 is the
        // only key that matches it.
        val key = ZcashSdk.deriveSpendingKey(
            phrase = phrase.words.joinToString(" "),
            network = ZcashNetwork.MAIN,
            passphrase = phrase.passphrase.ifEmpty { null },
        )
        return try {
            block(key)
        } finally {
            key.fill(0)
        }
    }

    // endregion

    // region state

    private suspend fun onSessionState(state: ZcashSessionState) {
        val previous = appliedSessionState
        if (previous?.balance != state.balance) onBalance(state.balance)
        if (previous?.transactions != state.transactions) {
            transactionsProvider.onTransactions(state.transactions)
        }
        if (previous?.latestHeight != state.latestHeight) onLatestHeight(state.latestHeight)
        appliedSessionState = state
        if (previous?.syncState != state.syncState) onSyncState(state.syncState)
    }

    private fun onSyncState(state: SyncState) {
        lastSyncing = state as? SyncState.Syncing
        if (state !is SyncState.Syncing) syncAnchor = null
        syncState = state.toAdapterState()
        logDiag()
    }

    private fun SyncState.toAdapterState(): AdapterState {
        if (syncState is AdapterState.Synced && isCaughtUpPoll()) return AdapterState.Synced
        return when (this) {
            // In this SDK `Stopped` only means no sync pass has run yet, not a failure.
            SyncState.Stopped, SyncState.Connecting -> AdapterState.Connecting
            SyncState.Synced -> AdapterState.Synced
            is SyncState.Failed -> AdapterState.NotSynced(error)
            is SyncState.Syncing -> syncingState(this)
        }
    }

    private fun SyncState.isCaughtUpPoll(): Boolean =
        this == SyncState.Stopped || this == SyncState.Connecting ||
            this is SyncState.Syncing && current >= target

    private fun syncingState(state: SyncState.Syncing): AdapterState {
        val anchor = syncAnchor ?: state.current.also { syncAnchor = it }
        val remained = (state.target - state.current).coerceAtLeast(0)
        val total = (state.target - anchor).takeIf { it > 0 }
        val progress = total?.let { ((it - remained).toDouble() / it * 100.0).coerceIn(0.0, 100.0) }
        return AdapterState.Syncing(progress = progress, blocksRemained = remained.toLong())
    }

    private fun onBalance(balance: PoolBalance) {
        poolBalance = balance
        balanceUpdatedSubject.onNext(Unit)
        startOneTimeAddressBalanceCheck()
        logDiag()
    }

    private fun onLatestHeight(height: Int) {
        latestHeight = height
        lastBlockUpdatedSubject.onNext(Unit)
        logDiag()
    }

    private fun startOneTimeAddressBalanceCheck() {
        if (balanceCheckJob?.isActive == true) return
        balanceCheckJob = scope.launch { checkTransparentAddressesBalance() }
    }

    private suspend fun checkTransparentAddressesBalance() {
        singleUseAddressManager.getAddressesForBalanceCheck().forEach { address ->
            val balance = try {
                walletOrNull { zcash, id -> zcash.transparentBalance(id, address) }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                zcashLogger.w { "Transparent balance check failed error=${error.zcashErrorName}" }
                null
            }
            if (balance != null && balance > 0) {
                singleUseAddressManager.updateAddressBalance(address, true)
            }
        }
    }

    // Privacy-safe diagnostic line for the stuck-pending investigation; only coarse
    // booleans/buckets and public block heights are logged, never amounts/keys/addresses.
    private fun logDiag() {
        val snapshot = readDiagSnapshot()
        val now = SystemClock.elapsedRealtime()
        val stateChanged = snapshot.syncStateDiscriminator != lastDiagSyncState
        val throttled = lastDiagLogTimeMs?.let { now - it < DIAG_INTERVAL_MS } == true
        if (!stateChanged && throttled) return

        lastDiagLogTimeMs = now
        lastDiagSyncState = snapshot.syncStateDiscriminator
        tryOrNull { zcashLogger.d { diagFields(snapshot).toString() } }
    }

    private fun readDiagSnapshot(): ZcashDiagSnapshot {
        val syncing = lastSyncing
        val balance = poolBalance?.forSpec(addressSpecTyped)
        return ZcashDiagSnapshot(
            pool = poolName,
            syncStateDiscriminator = syncState::class.simpleName ?: "Unknown",
            chainTipHeight = latestHeight.takeIf { it > 0 }?.toLong(),
            scannedHeight = syncing?.current?.toLong(),
            syncTargetHeight = syncing?.target?.toLong(),
            available = balance?.available?.convertZatoshiToZec(),
            changePending = balance?.changePending?.convertZatoshiToZec(),
            valuePending = balance?.valuePending?.convertZatoshiToZec(),
        )
    }

    override val debugInfo: String
        get() = ""

    override val balanceState: AdapterState
        get() = syncState

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = adapterStateUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()

    override val balanceData: BalanceData
        get() = balance.toBalanceData()

    override val balanceUpdatedFlow: Flow<Unit>
        get() = balanceUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()

    override val statusInfo: Map<String, Any>
        get() = linkedMapOf(
            "Last Block Info" to (lastBlockInfo ?: ""),
            "Sync State" to safeSyncStateLabel(syncState),
            "Birthday Height" to accountBirthday,
        )

    // endregion

    // region fee

    private val _fee: MutableStateFlow<BigDecimal> = MutableStateFlow(MINERS_FEE)

    /** Balance the published [_fee] was calculated for; null until one is published. */
    private var feeBalance: PoolBalance? = null
    private var feeGeneration = 0L
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()

    override val maxSpendableBalance: BigDecimal
        get() {
            val spendable = balance.available - fee.value.convertZecToZatoshi()
            return if (spendable <= 0) BigDecimal.ZERO else spendable.convertZatoshiToZec()
        }

    /**
     * Under ZIP-317 the fee depends on which pools and how many notes are spent, and after NU6.3
     * activation funds move between Orchard and Ironwood without changing the total — so the fee
     * follows the balance, not the first sync. [feeBalance] is the balance the published fee was
     * calculated for: a repeated `Synced` on the same balance costs nothing, while a calculation
     * that failed is not remembered as done.
     *
     * While the published fee is still the default one the balance is not enough to conclude the
     * fee is current — ZIP-317 also depends on the proposal target height, which changes at NU6.3
     * activation without touching any balance field — so it is recalculated on every trigger
     * until a real fee is known.
     */
    private suspend fun recalculateFeeOnChange(session: ZcashSession) {
        coroutineScope {
            var calculation: Job? = null
            session.state.collect { state ->
                if (state.syncState !is SyncState.Synced) return@collect
                val balance = state.balance
                val generation = sessionMutex.withLock {
                    if (balance == feeBalance && _fee.value != MINERS_FEE) null
                    else ++feeGeneration
                } ?: return@collect
                calculation?.cancel()
                calculation = launch { publishFee(session, balance, generation) }
            }
        }
    }

    /** Cancelling a planning call in flight is not guaranteed, so a superseded fee is dropped. */
    private suspend fun publishFee(
        session: ZcashSession,
        balance: PoolBalance,
        generation: Long,
    ) {
        val fee = calculateFee(balance.forSpec(addressSpecTyped).available) ?: return
        sessionMutex.withLock {
            if (this@ZcashAdapter.session !== session || feeGeneration != generation) return@withLock
            if (session.state.value.balance != balance) return@withLock
            _fee.value = fee
            feeBalance = balance
        }
    }

    /**
     * The fee of spending the whole balance: `recipientPaysFee` keeps that plan solvable, so one
     * planning pass answers what the old probe searched for by stepping the amount down.
     */
    private suspend fun calculateFee(available: Long): BigDecimal? {
        if (available <= 0) return MINERS_FEE
        val donateAddress = AppConfigProvider.donateAddresses[BlockchainType.Zcash] ?: return null
        return tryOrNull {
            walletOrNull { zcash, id ->
                val prepared = zcash.prepare(
                    account = id,
                    recipients = listOf(Recipient(address = donateAddress, amount = available)),
                    options = PaymentOptions(recipientPaysFee = true),
                )
                zcash.plan(prepared).fee.convertZatoshiToZec()
            }
        }
    }

    // endregion

    // region send

    override suspend fun validate(address: String): ZCashAddressType {
        if (address == receiveAddress) throw ZcashError.SendToSelfNotAllowed
        return when (zcashAddressKind(address)) {
            null -> throw ZcashError.InvalidAddress
            ZcashAddressKind.TRANSPARENT -> ZCashAddressType.Transparent
            ZcashAddressKind.SAPLING, ZcashAddressKind.TEX -> ZCashAddressType.Shielded
            ZcashAddressKind.UNIFIED -> ZCashAddressType.Unified
        }
    }

    override suspend fun send(
        amount: BigDecimal,
        address: String,
        memo: String,
    ): String {
        zcashLogger.d { "Send started" }
        return withSpendingKey { key ->
            broadcastSigned(listOf(recipient(amount, address, memo)), key)
        }
    }

    /**
     * Builds, signs and broadcasts in separate steps so a failure that provably precedes the
     * broadcast is marked as such: only then may the caller drop its pending row. A failure on the
     * broadcast itself, cancellation included, stays unmarked — the bytes may already be out.
     */
    private suspend fun broadcastSigned(
        recipients: List<Recipient>,
        spendingKey: ByteArray,
        options: PaymentOptions = PaymentOptions(),
    ): String {
        val (raw, height) = beforeBroadcast {
            requireWallet { zcash, id ->
                val prepared = zcash.prepare(account = id, recipients = recipients, options = options)
                val height = zcash.plan(prepared).height
                zcash.extract(zcash.sign(account = id, transaction = prepared, spendingKey = spendingKey)) to height
            }
        }
        reserveBeforeBroadcast(raw)
        val result = requireWallet { zcash, id -> zcash.broadcast(id, raw, height) }
        check(result.accepted) { "Broadcast rejected (${result.errorCode}): ${result.message}" }
        return result.message
    }

    private suspend fun <T> beforeBroadcast(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e as? NotBroadcastException ?: NotBroadcastException(e)
    }

    private suspend fun reserveBeforeBroadcast(raw: ByteArray) {
        val current = session ?: throw NotBroadcastException(
            IllegalStateException("Zcash wallet session is unavailable")
        )
        when (beforeBroadcast { current.reserveForBroadcast(raw) }) {
            is ZcashSessionResult.Success -> Unit
            ZcashSessionResult.Unavailable -> throw NotBroadcastException(
                IllegalStateException("Zcash wallet session is unavailable")
            )
        }
        when (current.refresh()) {
            is ZcashSessionResult.Success -> Unit
            ZcashSessionResult.Unavailable -> error("Zcash wallet session became unavailable")
        }
    }

    override suspend fun signOffline(request: OfflineSignRequest): SignedOfflineZcashTransaction {
        require(request is OfflineZcashSignRequest) { "OfflineZcashSignRequest is required" }
        val recipient = recipient(request.amount, request.address, request.memo)
        return withSpendingKey { key ->
            requireWallet { zcash, id ->
                val prepared = zcash.prepare(account = id, recipients = listOf(recipient))
                val fee = zcash.plan(prepared).fee
                val raw = zcash.extract(zcash.sign(account = id, transaction = prepared, spendingKey = key))
                SignedOfflineZcashTransaction(
                    rawHex = raw.toRawHexString(),
                    txHash = ZcashSdk.transactionId(raw).canonicalTransactionHash(),
                    fee = fee.convertZatoshiToZec(),
                )
            }
        }
    }

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val (zcashMetadata, raw, height) = beforeBroadcast {
            val zcashMetadata = metadata as? OfflineBroadcastMetadata.Zcash
                ?: throw UnsupportedException("Zcash raw broadcast requires P.CASH payload metadata")
            val normalizedRawHex = rawTransactionHex.trim()
            require(OfflineTransactionPayloadEncoder.isRawTransactionHex(normalizedRawHex)) {
                "Valid raw transaction hex is required"
            }
            Triple(zcashMetadata, normalizedRawHex.hexToByteArray(), requireWallet { zcash, _ -> zcash.latestHeight() })
        }
        reserveBeforeBroadcast(raw)
        return requireWallet { zcash, id -> zcash.broadcast(id, raw, height) }
            .toBroadcastResult(zcashMetadata.txHash)
    }

    private fun recipient(amount: BigDecimal, address: String, memo: String) = Recipient(
        address = address,
        amount = amount.convertZecToZatoshi(),
        memo = memo.takeIf { it.isNotBlank() },
    )

    override suspend fun getOwnAddresses(): List<String> =
        listOfNotNull(ownAddresses?.sapling, ownAddresses?.unified)

    /** Resolved before the pending row is registered, so the caller knows what is about to move. */
    suspend fun shieldingTarget(): ShieldingTarget = requireWallet { zcash, id ->
        val transparent = zcash.balance(id, SHIELDING_CONFIRMATIONS)[Pool.TRANSPARENT].available
        check(transparent > SHIELDING_THRESHOLD) { "Nothing to shield" }
        val shielded = requireNotNull(ownAddresses?.unified ?: ownAddresses?.orchard) {
            "No shielded receiver to shield into"
        }
        ShieldingTarget(address = shielded, amount = transparent)
    }

    suspend fun proposeShielding(target: ShieldingTarget): String = withSpendingKey { key ->
        broadcastSigned(
            recipients = listOf(Recipient(address = target.address, amount = target.amount)),
            spendingKey = key,
            options = PaymentOptions(
                sourcePools = PoolSet.of(Pool.TRANSPARENT),
                recipientPaysFee = true,
                confirmations = SHIELDING_CONFIRMATIONS,
            ),
        )
    }

    override suspend fun generateOneTimeAddress(): String? = try {
        val address = requireWallet { zcash, id -> zcash.nextTransparentAddress(id) }
        if (address == null) {
            singleUseAddressManager.getNextAddress()
        } else {
            singleUseAddressManager.saveNewAddress(address)
            address
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        zcashLogger.w { "Single-use transparent address failed error=${error.zcashErrorName}" }
        singleUseAddressManager.getNextAddress()
    }

    // endregion

    // region Ironwood migration

    /**
     * The Orchard balance that has to be moved to Ironwood, or `null` when migration is not
     * applicable. Orchard and Ironwood are both surfaced by the unified token, so only that
     * adapter can migrate.
     */
    val ironwoodMigrationRequiredBalance: BigDecimal?
        get() {
            if (addressSpecTyped != AddressSpecType.Unified) return null
            if (wallet.account.type !is AccountType.Mnemonic) return null
            if (syncState !is AdapterState.Synced) return null
            if (latestHeight < IRONWOOD_ACTIVATION_HEIGHT) return null
            val orchard = poolBalance?.get(Pool.ORCHARD) ?: return null
            // Migration spends the whole pool and fails while any Orchard note is still pending,
            // so offering it before everything is spendable only produces an unactionable error.
            if (orchard.available <= 0 || orchard.pending > 0) return null
            return orchard.available.convertZatoshiToZec()
        }

    /**
     * The migration moves notes one at a time, so the total fee is only known once every step has
     * run; what is offered up front is the per-step minimum multiplied by the steps still due.
     */
    suspend fun proposeIronwoodMigration(): IronwoodMigrationProposal {
        val available = checkNotNull(poolBalance?.get(Pool.ORCHARD)?.available?.takeIf { it > 0 }) {
            "No spendable Orchard balance"
        }
        val fee = requireWallet { zcash, id -> zcash.migrationStatus(id) }.remainingSteps() *
            MINERS_FEE_ZATOSHI
        return IronwoodMigrationProposal(
            amount = (available - fee).coerceAtLeast(0).convertZatoshiToZec(),
            fee = fee.convertZatoshiToZec(),
        )
    }

    /**
     * Runs the migration to completion and reports the first transaction it broadcast. It stops on
     * the first step that changes nothing — the next anchor block has not arrived yet — so what is
     * left is migrated by the next attempt rather than by paying for a step that does not advance.
     */
    suspend fun executeIronwoodMigration(): String = withSpendingKey { key ->
        val txIds = mutableListOf<String>()
        var status = beforeBroadcast { requireWallet { zcash, id -> zcash.migrationStatus(id) } }
        try {
            while (status.phase != MigrationPhase.COMPLETE) {
                val step = migrationStepAndRefresh(
                    step = { requireWallet { zcash, id -> zcash.migrationStep(id, key) } },
                    refresh = this@ZcashAdapter::refresh,
                )
                step.txid?.let(txIds::add)
                if (step.event == MigrationEvent.NOTHING_TO_DO || step.status == status) break
                status = step.status
            }
        } finally {
            rememberIronwoodMigration(txIds)
        }
        txIds.firstOrNull() ?: error("Migration produced no transaction")
    }

    /**
     * Once the transaction is mined and rescanned from chain its outputs are reported as change
     * and no longer as recipients of this account, so the "transfer to self" heuristic stops
     * matching. Only the recorded transaction id keeps the migration label.
     */
    private suspend fun rememberIronwoodMigration(transactionHashes: List<String>) =
        ironwoodMigrations.remember(wallet.account.id, transactionHashes)

    data class IronwoodMigrationProposal(val amount: BigDecimal, val fee: BigDecimal)

    /** The whole transparent balance moves; the recipient pays the fee out of it. */
    data class ShieldingTarget(val address: String, val amount: Long)

    // endregion

    // region transactions

    override val explorerTitle: String
        get() = "blockchair.com"

    override val transactionsState: AdapterState
        get() = syncState

    override val transactionsStateUpdatedFlowable: Flowable<Unit>
        get() = adapterStateUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER)

    override val lastBlockInfo: LastBlockInfo?
        get() = latestHeight.takeIf { it > 0 }?.let { LastBlockInfo(it) }

    override val lastBlockUpdatedFlowable: Flowable<Unit>
        get() = lastBlockUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER)

    override suspend fun getTransactions(
        from: TransactionRecord?,
        token: Token?,
        limit: Int,
        transactionType: FilterTransactionType,
        address: String?,
    ): List<TransactionRecord> {
        val fromParams = from?.let { Triple(it.transactionHash, it.timestamp, it.transactionIndex) }
        return transactionsProvider
            .getTransactions(fromParams, transactionType, address, limit)
            .map(::getTransactionRecord)
    }

    override fun getTransactionRecordsFlow(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): Flow<List<TransactionRecord>> =
        transactionsProvider.getNewTransactionsFlowable(transactionType, address)
            .map { transactions -> transactions.map(::getTransactionRecord) }

    override fun getTransactionsReloadSignalFlow(): Flow<Unit> =
        transactionsProvider.transactionsReloadSignalFlow

    override fun getTransactionUrl(transactionHash: String): String =
        "https://blockchair.com/zcash/transaction/$transactionHash"

    private fun getTransactionRecord(transaction: Transaction): TransactionRecord {
        val isIronwoodMigration = !transaction.isIncoming &&
            ironwoodMigrations.contains(wallet.account.id, transaction.txid)
        // A migration keeps the funds in the wallet, so the moved amount is what was received
        // back rather than the net change of the balance. Everything else takes the SDK's signed
        // value as it is: negative when funds leave.
        val amount = when {
            isIronwoodMigration -> transaction.totalReceived.convertZatoshiToZec()
            else -> transaction.value.convertZatoshiToZec()
        }
        return BitcoinTransactionRecord(
            token = wallet.token,
            uid = transaction.txid,
            transactionHash = transaction.txid,
            transactionIndex = transaction.id,
            blockHeight = transaction.height.takeIf { it > 0 },
            confirmationsThreshold = CONFIRMATIONS_THRESHOLD,
            timestamp = transaction.time,
            fee = transaction.fee.takeIf { it > 0 }
                ?.let { TransactionValue.CoinValue(wallet.token, it.convertZatoshiToZec()) },
            failed = false,
            lockInfo = null,
            conflictingHash = null,
            showRawTransaction = false,
            amount = amount,
            from = null,
            to = transaction.recipient?.let(::listOf),
            changeAddresses = null,
            sentToSelf = false,
            memo = transaction.memo,
            source = wallet.transactionSource,
            replaceable = false,
            transactionRecordType = if (transaction.isIncoming) {
                TransactionRecordType.BITCOIN_INCOMING
            } else {
                TransactionRecordType.BITCOIN_OUTGOING
            },
            isIronwoodMigration = isIronwoodMigration,
        )
    }

    // endregion

    enum class ZCashAddressType {
        Shielded, Transparent, Unified
    }

    sealed class ZcashError : Exception() {
        object InvalidAddress : ZcashError()
        object SendToSelfNotAllowed : ZcashError()
    }

    companion object {
        private const val CONFIRMATIONS_THRESHOLD = 10
        private const val SHIELDING_CONFIRMATIONS = 1
        private const val SHIELDING_THRESHOLD = 100_000L
        private const val DIAG_INTERVAL_MS = 30_000L

        /** NU6.3 activation on mainnet. */
        private const val IRONWOOD_ACTIVATION_HEIGHT = 3_428_143

        private const val MINERS_FEE_ZATOSHI = 10_000L
        val MINERS_FEE: BigDecimal = MINERS_FEE_ZATOSHI.convertZatoshiToZec()
    }
}

/** Which pools an address spec spends from; Unified holds Orchard and its Ironwood change. */
internal fun PoolBalance.forSpec(spec: AddressSpecType?): Balance = when (spec) {
    null, AddressSpecType.Shielded -> get(Pool.SAPLING)
    AddressSpecType.Transparent -> get(Pool.TRANSPARENT)
    AddressSpecType.Unified -> get(Pool.ORCHARD) + get(Pool.IRONWOOD)
}

internal fun Balance.toBalanceData() = BalanceData(
    available = available.convertZatoshiToZec(),
    pending = pending.convertZatoshiToZec(),
    timeLocked = locked.convertZatoshiToZec(),
)

private operator fun Balance.plus(other: Balance) = Balance(
    available = available + other.available,
    locked = locked + other.locked,
    changePending = changePending + other.changePending,
    valuePending = valuePending + other.valuePending,
)

/** Notes are split into standard denominations first, then moved one at a time. */
private fun MigrationStatus.remainingSteps(): Long =
    (standardNotes - migratedNotes).coerceAtLeast(0).toLong() + if (nonStandardNotes > 0) 1 else 0

internal fun BroadcastResult.toBroadcastResult(txHash: String): BroadcastRawTransactionResult = when {
    // An accepted broadcast reports the txid it assigned; a rejection reports the node's reason.
    accepted -> BroadcastRawTransactionResult(
        txHash = message.canonicalTransactionHash(),
        status = BroadcastRawTransactionStatus.Submitted,
    )

    message.isZcashAlreadyCommittedToBestChainError() -> BroadcastRawTransactionResult(
        txHash = txHash.canonicalTransactionHash(),
        status = BroadcastRawTransactionStatus.AlreadyKnown,
    )

    else -> throw ZcashException("Zcash raw transaction broadcast failed ($errorCode): $message")
}

internal fun zcashRestartDelayFor(attempt: Int, baseMs: Long, maxMs: Long): Long =
    (baseMs shl attempt.coerceAtMost(3)).coerceAtMost(maxMs)

internal suspend fun <T> migrationStepAndRefresh(
    step: suspend () -> T,
    refresh: suspend () -> Unit,
): T = try {
    step()
} finally {
    refresh()
}
