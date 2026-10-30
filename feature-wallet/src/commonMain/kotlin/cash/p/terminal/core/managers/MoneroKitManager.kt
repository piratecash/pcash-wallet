package cash.p.terminal.core.managers

import cash.p.terminal.core.onPollingStartedSuspend
import cash.p.terminal.core.onPollingStoppedSuspend
import cash.p.terminal.core.MoneroRescanException
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.MoneroAdapter
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.usecase.MoneroWalletStorage
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.utils.MoneroConfig
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.ledger.domain.LedgerAttachments
import cash.p.terminal.ledger.domain.LedgerNotFoundException
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledger.domain.isLedgerUnreachable
import cash.p.terminal.ledger.monero.LEDGER_MONERO_APP
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.data.MnemonicKind
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import com.m2049r.xmrwallet.data.DefaultNodes
import com.m2049r.xmrwallet.data.NodeInfo
import com.m2049r.xmrwallet.data.TxData
import com.m2049r.xmrwallet.data.UserNotes
import com.m2049r.xmrwallet.model.PendingTransaction
import com.m2049r.xmrwallet.model.TransactionInfo
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.model.WalletManager
import com.m2049r.xmrwallet.offline.RawMoneroBroadcastResult
import com.m2049r.xmrwallet.offline.SignedRawMoneroTransaction
import com.m2049r.xmrwallet.service.DaemonConnectResult
import com.m2049r.xmrwallet.service.LocalOpenResult
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.m2049r.xmrwallet.service.WalletCorruptedException
import com.piratecash.monero.net.MoneroHttpClient
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareKeyImageRefreshResult
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.core.sizeInMb
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.java.KoinJavaComponent.inject
import co.touchlab.kermit.Logger
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

private val moneroLogger = Logger.withTag("MoneroKitManager")

class MoneroKitManager(
    private val moneroWalletService: MoneroWalletService,
    private val backgroundManager: IBackgroundManager,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val connectivityManager: IConnectivityManager,
    private val dispatcherProvider: DispatcherProvider,
    private val moneroFileDao: MoneroFileDao,
    private val removeMoneroWalletFilesUseCase: IRemoveMoneroWalletFilesUseCase,
    private val networkErrorTracker: NetworkErrorTracker,
    private val offlineModeManager: OfflineModeManager,
) {
    // Serializes account-lifecycle mutations (activate / unlink / stop) so the process-global
    // MoneroHttpClient observer factory is set/cleared without racing a concurrent teardown.
    private val accountMutex = Mutex()
    private val pollingSessionCount = AtomicInteger(0)
    private val coroutineScope =
        CoroutineScope(dispatcherProvider.io + CoroutineExceptionHandler { _, throwable ->
            moneroLogger.e(throwable) { "Coroutine error" }
        })
    var moneroKitWrapper: MoneroKitWrapper? = null
    private val lifecycleJobs = mutableListOf<Job>()
    // Its own SupervisorJob: a failed close must not cancel coroutineScope and with it every later subscription.
    private val closeScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    // The old wallet's close outlives a cancelled caller; the next account-lock holder settles it first.
    @Volatile
    private var pendingClose: Deferred<Unit>? = null

    private var useCount = AtomicInteger(0)
    var currentAccount: Account? = null
        private set
    private val moneroKitStoppedSubject = PublishSubject.create<Unit>()
    private val moneroHardwareGateway: MoneroHardwareGateway by inject(
        MoneroHardwareGateway::class.java,
    )
    private val ledgerAttachments: LedgerAttachments by inject(LedgerAttachments::class.java)
    private val ledgerGateway: MoneroLedgerOperationGateway by inject(MoneroLedgerOperationGateway::class.java)
    private val ledgerUserOperations: LedgerUserOperations by inject(LedgerUserOperations::class.java)

    val kitStoppedObservable: Observable<Unit>
        get() = moneroKitStoppedSubject

    internal suspend fun <T> withExclusiveNativeWallet(
        block: suspend () -> T,
    ): T = withAccountLock {
        val wrapper = moneroKitWrapper
        if (wrapper == null) {
            block()
        } else {
            wrapper.withNativeWalletReleased(
                localOnly = isNetworkPaused(wrapper.account),
                block = block,
            )
        }
    }

    suspend fun getMoneroKitWrapper(account: Account): MoneroKitWrapper = withAccountLock {
        if (this.moneroKitWrapper != null && currentAccount != account) {
            stopKit() // also nulls moneroKitWrapper and clears the factory
        }

        if (this.moneroKitWrapper == null) {
            val accountType = account.type
            this.moneroKitWrapper = when {
                accountType is AccountType.MnemonicMonero ||
                        accountType is AccountType.Mnemonic ||
                        accountType is AccountType.HardwareDevice
                    -> createKitInstance(account)

                else -> throw UnsupportedAccountException()
            }
            // Install the passive network observer for THIS account before startKit triggers
            // node-selection pings, so transport errors are attributed to the active account.
            MoneroHttpClient.eventListenerFactory =
                NetworkErrorEventListener.Factory(BlockchainType.Monero, account.id, networkErrorTracker)
            var retryOnLifecycleEvent = false
            try {
                startKit()
            } catch (e: Throwable) {
                val partial = moneroKitWrapper
                var cleanupSucceeded = partial == null
                withContext(NonCancellable) {
                    try {
                        partial?.stop()
                        cleanupSucceeded = true
                    } catch (stopError: Throwable) {
                        e.addSuppressed(stopError)
                    }
                }
                retryOnLifecycleEvent =
                    cleanupSucceeded &&
                        accountType is AccountType.TrezorDevice &&
                        partial?.isRestartableAfterFailedStart == true &&
                        e.isRetryableTrezorStartupFailure()
                if (cleanupSucceeded && !retryOnLifecycleEvent) {
                    moneroKitWrapper = null
                    MoneroHttpClient.eventListenerFactory = null
                }
                if (!retryOnLifecycleEvent) throw e
            }
            subscribeToEvents(account)
            useCount.set(0)
            currentAccount = account
        }

        useCount.incrementAndGet()
        requireNotNull(moneroKitWrapper)
    }

    private fun createKitInstance(
        account: Account,
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = moneroWalletService,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = networkErrorTracker,
            moneroHardwareGateway = moneroHardwareGateway,
            connectivityManager = connectivityManager,
            ledgerUserOperations = ledgerUserOperations,
            ledgerGateway = ledgerGateway,
        )
    }

    suspend fun unlink(account: Account) = withAccountLock {
        if (account == currentAccount) {
            if (useCount.decrementAndGet() < 1) {
                try {
                    stopKit()
                } catch (error: Throwable) {
                    useCount.incrementAndGet()
                    throw error
                }
            }
        }
    }

    /**
     * Rescans [account] from [newHeight]. Operates only on the already-active wrapper
     * (never calls [getMoneroKitWrapper], which would bump [useCount] without a matching
     * [unlink] and leak it). For an inactive account, the wallet files are removed and the
     * new height persisted so the next [getMoneroKitWrapper] re-derives the wallet from it.
     */
    suspend fun rescan(account: Account, newHeight: Long) = withAccountLock {
        if (currentAccount?.id == account.id) {
            moneroKitWrapper?.rescan(newHeight)
        } else if (account.type is AccountType.TrezorDevice) {
            restoreSettingsManager.savePendingMoneroRescan(account, newHeight)
        } else {
            // Same fail-loud ordering as MoneroKitWrapper.resetWalletAndRestart: require the old
            // files to be gone before deleting the DAO record and committing the new height, so a
            // failed removal can never leave a stale wallet file behind a claimed new height.
            val removed = removeMoneroWalletFilesUseCase(account)
            if (!removed) {
                throw MoneroRescanException("Failed to remove Monero wallet files for account ${account.id}")
            }
            moneroFileDao.deleteAssociatedRecord(account.id)
            restoreSettingsManager.saveMoneroRestoreHeight(account, newHeight)
        }
    }

    suspend fun rescanIfActive(account: Account, newHeight: Long): Boolean = withAccountLock {
        if (currentAccount?.id != account.id) return@withAccountLock false
        moneroKitWrapper?.rescan(newHeight)
        true
    }

    suspend fun pauseNetwork(account: Account) = withAccountLock {
        if (account == currentAccount) moneroKitWrapper?.pause()
    }

    suspend fun resumeNetwork(account: Account) = withAccountLock {
        if (account == currentAccount) moneroKitWrapper?.resumeNetwork()
    }

    suspend fun startForPolling() {
        pollingSessionCount.onPollingStartedSuspend {
            resumeOrStartKit()
        }
    }

    suspend fun stopForPolling() {
        pollingSessionCount.onPollingStoppedSuspend(backgroundManager) {
            stopAndSaveKit()
        }
    }

    // Exclusive for every protocol: the single native wallet slot is free only once the old wallet closed.
    private suspend fun <T> withAccountLock(block: suspend () -> T): T = accountMutex.withLock {
        awaitPendingClose()
        block()
    }

    private suspend fun stopKit() {
        lifecycleJobs.forEach { it.cancel() }
        lifecycleJobs.clear()
        val wrapper = moneroKitWrapper
        pendingClose = closeScope.async { wrapper?.close() }
        awaitPendingClose()
    }

    private suspend fun awaitPendingClose() {
        val close = pendingClose ?: return
        try {
            close.join()
        } finally {
            // A cancelled caller settles a close that already ended; one still running is left to the next caller.
            if (close.isCompleted) settleClose(close)
        }
        close.await()
    }

    // A failed Deferred reports isCancelled: the old account keeps running; a closed one frees the native slot.
    private fun settleClose(close: Deferred<Unit>) {
        pendingClose = null
        if (close.isCancelled) {
            currentAccount?.let(::subscribeToEvents)
            return
        }
        currentAccount = null
        moneroKitWrapper = null
        MoneroHttpClient.eventListenerFactory = null
    }

    private suspend fun startKit() {
        val wrapper = moneroKitWrapper ?: return
        wrapper.start(localOnly = isNetworkPaused(wrapper.account))
    }

    // Reopening a Ledger wallet asks to export the view key again, so it stays open until the account changes
    // and only a running sync stores what it scanned.
    private suspend fun stopAndSaveKit() {
        val wrapper = moneroKitWrapper ?: return
        if (wrapper.account.moneroDeviceProtocol == MoneroDeviceProtocol.Proxy) {
            wrapper.requestCheckpoint()
        } else {
            wrapper.stop(saveWallet = true)
        }
    }

    // Under the account lock: the wrapper resumed is the current account's, never one a switch is closing.
    private suspend fun resumeOrStartKit() = withAccountLock {
        val wrapper = moneroKitWrapper ?: return@withAccountLock
        if (isNetworkPaused(wrapper.account)) {
            wrapper.start(localOnly = true)
        } else if (!wrapper.resume()) {
            startKit()
        }
    }

    private suspend fun resumeOrStartKitOnLifecycleEvent(): Boolean = try {
        resumeOrStartKit()
        true
    } catch (error: Throwable) {
        if (error.isRetryableTrezorStartupFailure() && moneroKitWrapper?.isRestartableAfterFailedStart == true) {
            false
        } else {
            throw error
        }
    }

    private fun isNetworkPaused(account: Account): Boolean =
        offlineModeManager.isNetworkPaused(account, BlockchainType.Monero)

    private fun subscribeToEvents(account: Account) {
        lifecycleJobs.forEach { it.cancel() }
        lifecycleJobs.clear()

        lifecycleJobs += coroutineScope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    resumeOrStartKitOnLifecycleEvent()
                } else if (state == BackgroundManagerState.EnterBackground) {
                    if (pollingSessionCount.get() == 0 && !backgroundKeepAliveManager.isKeepAlive(
                            BlockchainType.Monero
                        )
                    ) {
                        stopAndSaveKit()
                    } else {
                        Logger.withTag("TxPoller").d { "MoneroKit staying alive" }
                    }
                }
            }
        }
        lifecycleJobs += coroutineScope.launch {
            connectivityManager.networkAvailabilityFlow
                .onSubscription { emitConnectivityMissedAtStartup() }
                .collect { connected ->
                    if (isNetworkPaused(account)) return@collect
                    if (!connected) {
                        moneroKitWrapper?.onNetworkLost()
                    } else if (backgroundManager.inForeground) {
                        if (!resumeOrStartKitOnLifecycleEvent()) return@collect
                        // resumeOrStartKit is a no-op for an already running kit, so without an
                        // explicit refresh the state would stay NotSynced until the next callback.
                        moneroKitWrapper?.refresh()
                    }
                }
        }
        if (account.moneroDeviceProtocol == MoneroDeviceProtocol.Proxy) subscribeToLedger(account)
        lifecycleJobs += coroutineScope.launch {
            moneroKitWrapper?.let { w ->
                w.syncState.map { it is AdapterState.Synced }.distinctUntilChanged()
                    .collect { synced ->
                        if (synced) tryOrNull { w.saveSynced() }
                    }
            }
        }
    }

    private fun subscribeToLedger(account: Account) {
        lifecycleJobs += coroutineScope.launch {
            ledgerAttachments.attached.collect {
                if (backgroundManager.inForeground && !isNetworkPaused(account)) moneroKitWrapper?.onDeviceAttached()
            }
        }
        // The native refresh thread never runs for a Ledger, so new blocks arrive only through this loop.
        lifecycleJobs += coroutineScope.launch {
            while (true) {
                delay(LEDGER_SYNC_INTERVAL)
                if (backgroundManager.inForeground && !isNetworkPaused(account) &&
                    ledgerGateway.isMoneroAppOpen()
                ) {
                    moneroKitWrapper?.onLedgerAppOpen()
                }
            }
        }
    }

    // startKit() runs before the collector above subscribes and networkAvailabilityFlow has no
    // replay, so a change inside that window is dropped: re-emit it when the kit disagrees with
    // the device, and stay silent when they already agree.
    private suspend fun FlowCollector<Boolean>.emitConnectivityMissedAtStartup() {
        val connected = connectivityManager.isConnected.value
        val kitOffline = moneroKitWrapper?.syncState?.value is AdapterState.NotSynced
        when {
            !connected && !kitOffline -> emit(false)
            connected && kitOffline -> emit(true)
        }
    }
}

// A Monero block takes about two minutes; this keeps a connected Ledger well within one block.
private val LEDGER_SYNC_INTERVAL = 30.seconds

private val waitingForLedgerState = AdapterState.Syncing(substatus = AdapterState.Substatus.WaitingForLedger)

/** wallet_api reports success without scanning while its node lags behind, so only heights prove a sync. */
internal fun moneroScanReachedDaemon(walletHeight: Long, daemonHeight: Long, daemonTargetHeight: Long): Boolean =
    daemonHeight > 1 && daemonHeight >= daemonTargetHeight && walletHeight >= daemonHeight

private fun Throwable.isRetryableTrezorStartupFailure(): Boolean =
    this is HardwareWalletOperationException && when (error) {
        HardwareWalletErrorCode.DeviceNotFound,
        HardwareWalletErrorCode.AcquireTimeout -> true
        else -> false
    }

private fun RestoreSettingsManager.saveMoneroRestoreHeight(
    account: Account,
    height: Long,
) {
    require(height >= 0) { "Monero restore height must be non-negative" }
    val settings = settings(account, BlockchainType.Monero)
    settings.birthdayHeight = height
    save(settings, account, BlockchainType.Monero)
}

interface MoneroNativeWalletRuntime {
    suspend fun <T> withExclusiveWallet(block: suspend () -> T): T
}

internal fun shouldApplyHardwareRestoreHeight(
    currentHeight: Long,
    targetHeight: Long,
    hasPendingRescan: Boolean,
): Boolean = hasPendingRescan || currentHeight != targetHeight

class DefaultMoneroNativeWalletRuntime(
    private val moneroKitManager: MoneroKitManager,
) : MoneroNativeWalletRuntime {
    override suspend fun <T> withExclusiveWallet(block: suspend () -> T): T =
        moneroKitManager.withExclusiveNativeWallet(block)
}

/**
 * A single read of the native/service facts that authorize a callback or hardware spend.
 * Keeping this boundary injectable lets JVM tests exercise callback handling without loading JNI.
 */
data class MoneroWalletHealthSnapshot(
    val callbackWalletIsCurrent: Boolean,
    val nativeStatusIsOk: Boolean,
    val nativeConnectionIsConnected: Boolean,
    val serviceConnectionIsConnected: Boolean,
    val walletIsSynchronized: Boolean,
    val nativeConnectionStatus: ConnectionStatus? = null,
    val nativeStatusError: String? = null,
) {
    val isCurrentAndConnected: Boolean
        get() = callbackWalletIsCurrent &&
            nativeStatusIsOk &&
            nativeConnectionIsConnected &&
            serviceConnectionIsConnected

    val isFullyHealthy: Boolean
        get() = isCurrentAndConnected && walletIsSynchronized
}

interface MoneroWalletHealthReader {
    fun snapshot(callbackWallet: Wallet?): MoneroWalletHealthSnapshot
}

/** Points the native wallet manager at a node; injectable so JVM tests run without JNI. */
interface MoneroDaemonSelector {
    suspend fun selectDaemon()
}

private object AutoMoneroDaemonSelector : MoneroDaemonSelector {
    override suspend fun selectDaemon() {
        val node = MoneroConfig.autoSelectNode() ?: NodeInfo.fromString(DefaultNodes.entries.first().uri)
        WalletManager.getInstance().setDaemon(node)
    }
}

/** Called only under the wrapper's lifecycle lock; injectable so JVM tests run without JNI. */
interface MoneroSubaddressSource {
    fun read(): List<MoneroSubaddressInfo>?
    fun create(): String
}

private class ServiceMoneroSubaddressSource(
    private val moneroWalletService: MoneroWalletService,
) : MoneroSubaddressSource {
    override fun read(): List<MoneroSubaddressInfo>? {
        val wallet = moneroWalletService.wallet ?: return null
        return (0 until wallet.numSubaddresses).map { index ->
            val subaddress = wallet.getSubaddressObject(index)
            MoneroSubaddressInfo(
                index = subaddress.addressIndex,
                address = subaddress.address,
                receivedAmount = subaddress.amount
            )
        }
    }

    override fun create(): String =
        checkNotNull(moneroWalletService.wallet) { "Monero wallet not initialized" }.newSubaddress
}

private class ServiceMoneroWalletHealthReader(
    private val moneroWalletService: MoneroWalletService,
) : MoneroWalletHealthReader {
    override fun snapshot(callbackWallet: Wallet?): MoneroWalletHealthSnapshot {
        val serviceConnectionIsConnected = tryOrNull { moneroWalletService.connectionStatus } ==
            ConnectionStatus.ConnectionStatus_Connected
        val callbackWalletIsCurrent = callbackWallet != null &&
            callbackWallet === tryOrNull { moneroWalletService.wallet }
        if (!callbackWalletIsCurrent) {
            return MoneroWalletHealthSnapshot(
                callbackWalletIsCurrent = false,
                nativeStatusIsOk = false,
                nativeConnectionIsConnected = false,
                serviceConnectionIsConnected = serviceConnectionIsConnected,
                walletIsSynchronized = false,
            )
        }
        val status = tryOrNull { callbackWallet.status }
        val nativeConnectionStatus = tryOrNull { callbackWallet.connectionStatus }
        return MoneroWalletHealthSnapshot(
            callbackWalletIsCurrent = true,
            nativeStatusIsOk = status?.isOk == true,
            nativeConnectionIsConnected =
                nativeConnectionStatus == ConnectionStatus.ConnectionStatus_Connected,
            serviceConnectionIsConnected = serviceConnectionIsConnected,
            walletIsSynchronized = tryOrNull { callbackWallet.isSynchronized } == true,
            nativeConnectionStatus = nativeConnectionStatus,
            nativeStatusError = status?.errorString,
        )
    }
}

private data class MoneroWalletCredentials(
    val fileName: String,
    val password: String,
)

/** Keeps the message `isNoInternetException()` and the NotSynced presentation rely on. */
internal class MoneroNodeUnreachableException : Exception("No internet connection")

private const val CONTROLLED_REFRESH_FINALIZATION_TIMEOUT_MILLIS = 30_000L

internal fun interface ControlledHardwareRefreshAborter {
    fun abort(error: Throwable)
}

internal interface ControlledHardwareRefreshOperation {
    suspend fun run(aborter: ControlledHardwareRefreshAborter)
    fun abort(error: Throwable)
}

internal class ControlledHardwareRefreshCoordinator(
    private val scope: CoroutineScope,
) {
    private class Generation(
        val operation: Deferred<Unit>,
        var waiterCount: Int,
    )

    private val mutex = Mutex()
    private var generation: Generation? = null

    suspend fun execute(refreshOperation: ControlledHardwareRefreshOperation) {
        val acquiredGeneration = acquireOperation(refreshOperation)
        try {
            acquiredGeneration.operation.await()
        } finally {
            withContext(NonCancellable) {
                releaseWaiter(acquiredGeneration)
            }
        }
    }

    private suspend fun acquireOperation(
        refreshOperation: ControlledHardwareRefreshOperation,
    ): Generation {
        while (true) {
            val acquiredGeneration = mutex.withLock {
                val current = generation
                when {
                    current == null || current.operation.isCompleted -> {
                        Generation(
                            operation = scope.async {
                                val aborter =
                                    OneShotControlledHardwareRefreshAborter(refreshOperation::abort)
                                // The operation owns its lifecycle boundary and must abort before
                                // releasing it. A coordinator-level fallback would run outside that
                                // boundary and could race a queued wallet stop/restart.
                                refreshOperation.run(aborter)
                            },
                            waiterCount = 1,
                        ).also { generation = it }
                    }

                    current.operation.isCancelled -> current
                    else -> {
                        current.waiterCount += 1
                        current
                    }
                }
            }
            if (!acquiredGeneration.operation.isCancelled) return acquiredGeneration
            acquiredGeneration.operation.join()
            mutex.withLock {
                if (
                    generation === acquiredGeneration &&
                    acquiredGeneration.waiterCount == 0 &&
                    acquiredGeneration.operation.isCompleted
                ) {
                    generation = null
                }
            }
        }
    }

    private suspend fun releaseWaiter(acquiredGeneration: Generation) {
        val cancelOperation = mutex.withLock {
            acquiredGeneration.waiterCount -= 1
            if (
                acquiredGeneration.waiterCount == 0 &&
                generation === acquiredGeneration &&
                !acquiredGeneration.operation.isCompleted
            ) {
                acquiredGeneration.operation.cancel()
                true
            } else {
                false
            }
        }
        if (cancelOperation) {
            acquiredGeneration.operation.join()
            mutex.withLock {
                if (generation === acquiredGeneration) generation = null
            }
        }
    }
}

private class OneShotControlledHardwareRefreshAborter(
    private val action: (Throwable) -> Unit,
) : ControlledHardwareRefreshAborter {
    private var aborted = false

    override fun abort(error: Throwable) {
        if (aborted) return
        aborted = true
        action(error)
    }
}

internal sealed interface HardwareStartupRecovery {
    data object ExplicitColdRecovery : HardwareStartupRecovery
    data class LiveRefresh(val state: MoneroSpentReconciliationState) : HardwareStartupRecovery
}

internal fun hardwareStartupRecovery(
    durableState: MoneroSpentReconciliationState,
): HardwareStartupRecovery =
    when (durableState) {
        MoneroSpentReconciliationState.ExplicitColdRecoveryPending ->
            HardwareStartupRecovery.ExplicitColdRecovery
        MoneroSpentReconciliationState.MigrationReplayRequired ->
            HardwareStartupRecovery.LiveRefresh(MoneroSpentReconciliationState.MigrationReplayPending)
        MoneroSpentReconciliationState.MigrationReplayPending ->
            HardwareStartupRecovery.LiveRefresh(durableState)
        else -> HardwareStartupRecovery.LiveRefresh(MoneroSpentReconciliationState.LiveRefreshPending)
    }

internal fun MoneroSpentReconciliationState.normalizedLiveRefreshState(): MoneroSpentReconciliationState =
    when (this) {
        MoneroSpentReconciliationState.MigrationReplayRequired,
        MoneroSpentReconciliationState.MigrationReplayPending ->
            MoneroSpentReconciliationState.MigrationReplayPending

        MoneroSpentReconciliationState.ExplicitColdRecoveryPending ->
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending

        else -> MoneroSpentReconciliationState.LiveRefreshPending
    }

internal fun MoneroSpentReconciliationState.requiresControlledRefreshFinalization(): Boolean =
    this != MoneroSpentReconciliationState.ExplicitColdRecoveryPending

internal suspend fun awaitControlledRefreshFinalization(
    finalization: Deferred<Unit>,
    timeoutMillis: Long,
) {
    try {
        withTimeout(timeoutMillis) {
            finalization.await()
        }
    } catch (_: TimeoutCancellationException) {
        throw HardwareWalletOperationException(
            HardwareWalletErrorCode.Network,
            "Timed out waiting for Monero controlled refresh finalization",
        )
    }
}

class MoneroKitWrapper(
    private val moneroWalletService: MoneroWalletService,
    private val restoreSettingsManager: RestoreSettingsManager,
    val account: Account,
    private val dispatcherProvider: DispatcherProvider,
    private val networkErrorTracker: NetworkErrorTracker,
    private val moneroHardwareGateway: MoneroHardwareGateway,
    private val connectivityManager: IConnectivityManager,
    private val ledgerUserOperations: LedgerUserOperations,
    private val ledgerGateway: MoneroLedgerOperationGateway,
    private val walletHealthReader: MoneroWalletHealthReader =
        ServiceMoneroWalletHealthReader(moneroWalletService),
    private val daemonSelector: MoneroDaemonSelector = AutoMoneroDaemonSelector,
    private val subaddressSource: MoneroSubaddressSource = ServiceMoneroSubaddressSource(moneroWalletService),
) : MoneroWalletService.Observer {
    private val moneroFileDao: MoneroFileDao by inject(MoneroFileDao::class.java)
    private val moneroWalletUseCase: MoneroWalletUseCase by inject(MoneroWalletUseCase::class.java)
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase by inject(
        ValidateMoneroHeightUseCase::class.java
    )
    private val removeMoneroWalletFilesUseCase: IRemoveMoneroWalletFilesUseCase by inject(
        IRemoveMoneroWalletFilesUseCase::class.java
    )
    private val moneroWalletStorage: MoneroWalletStorage by inject(MoneroWalletStorage::class.java)
    private val getMoneroWalletFilesNameUseCase: IGetMoneroWalletFilesNameUseCase by inject(
        IGetMoneroWalletFilesNameUseCase::class.java
    )
    private val logger = AppLogger("monero-kit").getScoped(account.id)

    @Volatile
    private var lastLoggedConnectionStatus: ConnectionStatus? = null
    private var lastLoggedSyncProgress: Int = -1

    @Volatile
    private var isStarted = false
    @Volatile
    private var nativeCloseSequence = 0
    private var isPaused = false
    // Opened from disk because no node answered; refresh/resume may only attach a node, never close it.
    private var openedWithoutNode = false
    @Volatile
    private var startInProgress = false

    internal val isRestartableAfterFailedStart: Boolean
        get() = !isStarted && !startInProgress
    private var nativeStoreFaulted = false
    private var controlledLiveRefreshPending = false
    private var controlledLiveRefreshCommitted = false
    @Volatile
    private var hardwareRescanPending = false
    private var controlledRefreshFinalization: CompletableDeferred<Unit>? = null
    // Ledger sync changes wait here until a store succeeds; the restore height lives only in the keys file.
    private var restoreHeightUnstored = false
    // The pending restore height a block was observed for: only then has the rescan from it begun.
    private var rescanObservedFor: Long? = null
    private var storedChainHeight: Long? = null
    private var explicitColdRecoveryPending = false
    @Volatile
    private var keyImageSyncSession: Long? = null
    private val lifecycleMutex = Mutex()
    // Latched on the refresh thread when the Ledger refresh is asked to end at its next batch boundary.
    @Volatile
    private var proxySyncEarlyEnd: ProxySyncEarlyEnd? = null
    @Volatile
    private var checkpointRequested = false
    // A device failure may have corrupted the open wallet's memory, so it must never be stored.
    @Volatile
    private var walletTainted = false

    // Closed by the manager: a stale reference, e.g. an old adapter or a late collector, must not reopen the wallet.
    private var retired = false
    // The Ledger sync runs here, not in its callers, so cancelling a caller can never reach a running scan.
    private val proxySyncScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val proxySyncLock = Mutex()
    private var proxySync: Job? = null
    private var pendingProxySyncPass: ProxySyncPass? = null
    // Under proxySyncLock: interruptions not yet done, which no new sync may overtake.
    private var pendingInterruptions = 0
    // Interruptions waiting for lifecycleMutex; the holder's refresh ends at its next batch boundary for them.
    private val interruptionsWaiting = AtomicInteger()
    // Lets Receive skip a Ledger sync that holds the lock while it waits for the device; only amounts may lag.
    @Volatile
    private var subaddressesSnapshot: List<MoneroSubaddressInfo>? = null
    private val controlledRefreshCoordinator =
        ControlledHardwareRefreshCoordinator(CoroutineScope(SupervisorJob() + dispatcherProvider.io))
    private val spentStatusReconciler = MoneroSpentStatusReconciler(dispatcherProvider)
    private val spentStatusRecoveryOperations =
        object : MoneroSpentStatusRecoveryOperations<Wallet> {
            override fun isFullyHealthy(wallet: Wallet): Boolean =
                walletHealthReader.snapshot(wallet).isFullyHealthy

            override fun hasUnknownKeyImages(wallet: Wallet): Boolean =
                wallet.hasUnknownKeyImages()

            override suspend fun performPreservingRescan(
                wallet: Wallet,
                request: MoneroSpentStatusRescanRequest,
            ) {
                withPausedHardwareWallet(wallet, block = request::armAndRequest)
            }

            override fun requestPreservingRescan(wallet: Wallet) {
                wallet.rescanBlockchainAsyncPreserveKeyImages()
            }

            override suspend fun storeReconciledWallet(wallet: Wallet) {
                withPausedHardwareWallet(wallet, block = {
                    storeHardwareWallet(wallet, "Failed to store reconciled Monero wallet")
                })
            }

            override fun persistReady() {
                restoreSettingsManager.saveMoneroSpentReconciliationState(
                    account,
                    MoneroSpentReconciliationState.Ready,
                )
            }
        }
    private val spentStatusRecovery = MoneroSpentStatusRecovery(
        spentStatusReconciler,
        spentStatusRecoveryOperations,
    )
    private val deviceProtocol = account.moneroDeviceProtocol
    private val coldProtocol = deviceProtocol == MoneroDeviceProtocol.Cold
    private val proxyProtocol = deviceProtocol == MoneroDeviceProtocol.Proxy
    val hardwareWallet: Boolean
        get() = deviceProtocol != MoneroDeviceProtocol.None

    private val _spendReadiness = MutableStateFlow(
        if (hardwareWallet) MoneroSpendReadiness.Syncing else MoneroSpendReadiness.Ready,
    )
    val spendReadiness = _spendReadiness.asStateFlow()

    @Volatile
    private var storedForSync = false

    private val _syncState = MutableStateFlow<AdapterState>(AdapterState.Syncing())
    val syncState = _syncState.asStateFlow()

    private val _lastBlockInfoFlow = MutableStateFlow<LastBlockInfo?>(null)
    val lastBlockInfoFlow = _lastBlockInfoFlow.asStateFlow()
    private var cachedTotalHeight: Long = 0

    private var walletFileNameForStatus: String? = null

    private val _transactionsStateUpdatedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val transactionsStateUpdatedFlow = _transactionsStateUpdatedFlow.asSharedFlow()


    private suspend fun restoreFromBip39(
        account: Account,
        height: Long
    ) {
        logger.info("restoreFromBip39: height=$height")
        val accountType = account.type as? AccountType.Mnemonic
            ?: throw UnsupportedAccountException()
        val restoredAccount = moneroWalletUseCase.restoreFromBip39(
            accountType.words,
            accountType.passphrase,
            height
        ) ?: throw IllegalStateException("Failed to restore account from 12 words")
        moneroFileDao.insert(
            MoneroFileRecord(
                fileName = SecretString(restoredAccount.walletInnerName),
                password = SecretString(restoredAccount.password),
                accountId = account.id
            )
        )
    }

    suspend fun start(
        fixIfCorruptedFile: Boolean = true,
        localOnly: Boolean = false,
    ) = withLifecycleLock(LifecycleInterruption.Preempt) {
        startInternal(fixIfCorruptedFile, localOnly)
    }

    private suspend fun startInternal(
        fixIfCorruptedFile: Boolean = true,
        localOnly: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            check(!retired) { "The Monero wallet of a closed kit cannot be reopened" }
            if (!isStarted) {
                startInProgress = true
                walletTainted = false
                logger.info("start: requested, fixIfCorruptedFile=$fixIfCorruptedFile, isStarted=$isStarted")
                lastLoggedSyncProgress = -1
                lastLoggedConnectionStatus = null
                storedForSync = false
                // A previously synchronized hardware wallet must never retain its spend
                // authorization while a new native session is being opened.
                if (hardwareWallet) {
                    _spendReadiness.value = MoneroSpendReadiness.Syncing
                }
                publishSyncState(AdapterState.Connecting)
                try {
                    // Native start can synchronously invoke onWalletStarted().  Arm the current
                    // session before that call so the callback belongs to this start attempt.
                    activateReconciliationSession()
                    walletFileNameForStatus = null
                    val credentials = resolveWalletCredentials()
                    walletFileNameForStatus = credentials.fileName

                    if (localOnly) {
                        openLocalOnly(credentials)
                        return@withContext
                    }

                    if (proxyProtocol && !ledgerGateway.isDevicePresent) throw LedgerNotFoundException()

                    daemonSelector.selectDaemon()

                    /*val walletFolder: File = MoneroWalletFiles.root(App.instance)
                    val walletKeyFile = File(walletFolder, "$walletFileName.keys")
                    fixCorruptedWalletFile(walletKeyFile.absolutePath, walletPassword)*/

                    moneroWalletService.setObserver(this@MoneroKitWrapper)
                    logger.info("start: invoking startService for walletFileName=${credentials.fileName}")
                    val startedOnline = startAccountServiceOrOpenLocally(
                        credentials.fileName,
                        credentials.password,
                        fixIfCorruptedFile,
                    )
                    if (!startedOnline) return@withContext
                    isStarted = true
                    refreshSubaddressesSnapshot()
                    // The open publishes the wallet; its scan runs as a sync, so a switch never waits for it.
                    if (proxyProtocol) requestProxySync(ProxySyncPass.SyncStarted)
                    if (coldProtocol && controlledLiveRefreshPending) {
                        awaitControlledHardwareRefreshFinalization()
                    } else if (coldProtocol && explicitColdRecoveryPending) {
                        syncKeyImagesLocked()
                        explicitColdRecoveryPending = false
                    }
                    logger.info(
                        "start: completed startService, connection=${moneroWalletService.connectionStatus}, " +
                                "walletStatus=${moneroWalletService.wallet?.status}"
                    )
                    fixWalletHeight()
                } catch (e: Exception) {
                    handleStartFailure(e)
                } finally {
                    startInProgress = false
                }
            }
        }

    // Opening a Ledger wallet reads its keys from the device even without a node.
    private suspend fun openLocalOnly(credentials: MoneroWalletCredentials) {
        if (proxyProtocol) {
            moneroHardwareGateway.executeUnattended(account) { awaitingViewKeyExport { startLocalOnly(credentials) } }
        } else {
            startLocalOnly(credentials)
        }
    }

    // Loading a Ledger wallet waits for the user to allow the view-key export on the device.
    private fun <T> awaitingViewKeyExport(open: () -> T): T {
        ledgerUserOperations.beginViewKeyExport()
        try {
            return open()
        } finally {
            ledgerUserOperations.endViewKeyExport()
        }
    }

    private fun startLocalOnly(credentials: MoneroWalletCredentials) {
        moneroWalletService.setObserver(this)
        when (val result = moneroWalletService.startOffline(credentials.fileName, credentials.password)) {
            LocalOpenResult.Opened -> {
                isStarted = true
                isPaused = true
                refreshSubaddressesSnapshot()
                _transactionsStateUpdatedFlow.tryEmit(Unit)
            }

            is LocalOpenResult.Failed -> {
                _syncState.value = AdapterState.NotSynced(IllegalStateException(result.error.orEmpty()))
            }
        }
    }

    private suspend fun resolveWalletCredentials(): MoneroWalletCredentials =
        when (val accountType = account.type) {
            is AccountType.MnemonicMonero -> mnemonicMoneroCredentials(accountType)
            is AccountType.Mnemonic -> mnemonicCredentials()
            is AccountType.HardwareDevice -> deviceCredentials()
            else -> throw UnsupportedAccountException()
        }

    private suspend fun mnemonicMoneroCredentials(
        accountType: AccountType.MnemonicMonero,
    ): MoneroWalletCredentials {
        logger.info("start: using AccountType.MnemonicMonero")
        if (!File(moneroWalletStorage.walletsRoot(), accountType.walletInnerName).exists()) {
            moneroLogger.d { "Restoring Monero wallet from mnemonic..." }
            logger.info("start: wallet file does not exist, restoring from mnemonic")
            moneroWalletUseCase.restore(
                words = accountType.words,
                height = getBirthdayHeight(account) ?: accountType.height,
                crazyPassExisting = accountType.password,
                walletInnerNameExisting = accountType.walletInnerName,
            )
        }
        return MoneroWalletCredentials(accountType.walletInnerName, accountType.password)
    }

    private suspend fun mnemonicCredentials(): MoneroWalletCredentials {
        logger.info("start: using AccountType.Mnemonic")
        if (moneroFileDao.getAssociatedRecord(account.id) == null) {
            logger.info("start: no associated wallet files, restoring from mnemonic")
            val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
            val height = restoreSettings.birthdayHeight
                ?: validateMoneroHeightUseCase.getTodayHeight()
            check(height != -1L) { "Monero restore height can't be -1" }
            restoreFromBip39(account = account, height = height)
        }
        val record = requireNotNull(moneroFileDao.getAssociatedRecord(accountId = account.id)) {
            "Account does not have a valid Monero file association"
        }
        return MoneroWalletCredentials(record.fileName.value, record.password.value)
    }

    private suspend fun deviceCredentials(): MoneroWalletCredentials {
        val record = requireNotNull(moneroFileDao.getAssociatedRecord(account.id)) {
            "Hardware account has no Monero wallet files"
        }
        return MoneroWalletCredentials(record.fileName.value, record.password.value)
    }

    /** An unreachable node must not hide the stored balance and history: open the wallet from disk instead. */
    internal suspend fun startAccountServiceOrOpenLocally(
        walletFileName: String,
        walletPassword: String,
        fixIfCorruptedFile: Boolean,
    ): Boolean = try {
        startAccountService(walletFileName, walletPassword, fixIfCorruptedFile)
        true
    } catch (e: MoneroNodeUnreachableException) {
        logger.info("start: node unreachable, opening wallet locally")
        startLocalOnly(MoneroWalletCredentials(walletFileName, walletPassword))
        if (isStarted) {
            openedWithoutNode = true
            _syncState.value = AdapterState.NotSynced(e)
        }
        false
    }

    internal suspend fun startAccountService(
        walletFileName: String,
        walletPassword: String,
        fixIfCorruptedFile: Boolean,
    ) {
        if (proxyProtocol) {
            openProxyWallet(walletFileName, walletPassword)
        } else if (coldProtocol) {
            val durableState = restoreSettingsManager.moneroSpentReconciliationState(account)
            when (val recovery = hardwareStartupRecovery(durableState)) {
                HardwareStartupRecovery.ExplicitColdRecovery -> {
                    explicitColdRecoveryPending = true
                    moneroHardwareGateway.execute(account) {
                        startHardwareService(walletFileName, walletPassword)
                    }
                    return
                }

                is HardwareStartupRecovery.LiveRefresh -> {
                    val refreshState = recovery.state
                    restoreSettingsManager.saveMoneroSpentReconciliationState(account, refreshState)
                    controlledLiveRefreshPending = true
                    controlledLiveRefreshCommitted = false
                    moneroHardwareGateway.execute(account) {
                        try {
                            startHardwareServicePaused(walletFileName, walletPassword)
                            // A pending durable refresh has not established a safe native session.
                            if (durableState != MoneroSpentReconciliationState.LiveRefreshPending) {
                                refreshHardwareKeyImagesLeaseOwned(refreshState)
                                controlledLiveRefreshCommitted = true
                            }
                        } catch (error: Throwable) {
                            abortControlledHardwareWallet(error)
                            throw error
                        }
                    }
                    if (durableState == MoneroSpentReconciliationState.LiveRefreshPending) {
                        resumeInitialHardwareCatchUp()
                    }
                }
            }
        } else {
            startService(walletFileName, walletPassword, fixIfCorruptedFile)
        }
    }

    private fun handleStartFailure(error: Exception) {
        deactivateReconciliationSession()
        if (hardwareWallet) {
            val walletClosed = closeHardwareWalletAfterFailedStart(error)
            // The failed start cannot authorize reconciliation or spending, but a native wallet
            // that cleanup could not close must remain owned so a later stop() retries it.
            isStarted = !walletClosed
            isPaused = !walletClosed
        }
        if (coldProtocol) {
            // A failed start is never a valid session, even if the native cleanup also failed.
            _spendReadiness.value = MoneroSpendReadiness.ReconciliationFailed
        }
        _syncState.value = AdapterState.NotSynced(error)
        logger.warning("start: failed with exception", error)
        moneroLogger.e(error) { "Failed to start Monero wallet" }
        if (coldProtocol) throw error
    }

    private fun closeHardwareWalletAfterFailedStart(operationError: Throwable): Boolean {
        val wallet = moneroWalletService.wallet ?: return true
        return try {
            val closed = moneroWalletService.stop(false)
            if (!closed) {
                operationError.addSuppressed(
                    wallet.status.toHardwareWalletCloseFailure(
                        "Failed to close Monero hardware wallet after start failure",
                    ),
                )
            }
            closed
        } catch (cleanupError: Throwable) {
            operationError.addSuppressed(cleanupError)
            false
        }
    }

    private fun startHardwareService(
        walletFileName: String,
        walletPassword: String,
    ) {
        val status = moneroWalletService.start(walletFileName, walletPassword)
        recordNativeConnectionError(status?.connectionStatus, status?.errorString)
        if (status?.isOk != true) throw status.hardwareFailure()
    }

    private fun startHardwareServicePaused(
        walletFileName: String,
        walletPassword: String,
    ) {
        val status = moneroWalletService.startPaused(walletFileName, walletPassword)
        recordNativeConnectionError(status?.connectionStatus, status?.errorString)
        if (status?.isOk != true) throw status.hardwareFailure()
    }

    // A separate session: the view-key prompt must never be torn by a user operation. The scan follows as a sync.
    private suspend fun openProxyWallet(walletFileName: String, walletPassword: String) {
        moneroHardwareGateway.executeUnattended(account) { _ ->
            val session = requireActiveReconciliationSession()
            // The Ledger asks to export the view key on every open.
            val exportPrompt = AdapterState.Syncing(substatus = AdapterState.Substatus.ConfirmViewKeyExport)
            setSyncStateForSession(session, exportPrompt)
            awaitingViewKeyExport { startHardwareServicePaused(walletFileName, walletPassword) }
            // A first sync put off before its first block must not leave the finished prompt on screen.
            setSyncStateForSession(session, AdapterState.Connecting)
            refreshSubaddressesSnapshot()
        }
    }

    /** A failed sync leaves an open wallet NotSynced until the next trigger; a put-off one keeps the state it found. */
    private suspend fun syncProxyWallet() {
        val stateBefore = _syncState.value
        var deviceFailed = false
        var reachedDevice = false
        proxySyncEarlyEnd = null
        // The device may be busy elsewhere for long: replace a stale error, and put it back unless the device answered.
        val failedState = (_syncState.value as? AdapterState.NotSynced)
            ?.takeIf { _syncState.compareAndSet(it, waitingForLedgerState) }
        try {
            moneroHardwareGateway.executeYielding(account) {
                reachedDevice = true
                try {
                    syncProxyWalletLeaseOwned()
                } finally {
                    deviceFailed = !ledgerGateway.isSessionIntact
                }
            }
        } catch (error: Exception) {
            val putOffState = if (reachedDevice && failedState != null) waitingForLedgerState else stateBefore
            if (deviceFailed) closeAfterDeviceFailure(error) else onProxySyncFailed(error, putOffState)
        } finally {
            if (!reachedDevice) failedState?.let { _syncState.compareAndSet(waitingForLedgerState, it) }
        }
    }

    // wallet2 may hold state corrupted by the failed device, so the wallet closes unsaved.
    private suspend fun closeAfterDeviceFailure(error: Exception) {
        walletTainted = true
        try {
            withContext(NonCancellable) { stopInternal(saveWallet = false) }
        } finally {
            publishProxySyncFailure(error)
        }
        logger.warning("sync: Ledger failed mid-sync, wallet closed unsaved", error)
    }

    private fun onProxySyncFailed(error: Exception, stateBefore: AdapterState) {
        // An interruption's cancel may replace the session's own result, so it is a put-off too.
        if (error is CancellationException || error is LedgerSessionDeferredException ||
            error is LedgerSessionYieldedException
        ) {
            onProxySyncPutOff(error, stateBefore)
            if (error is CancellationException) throw error
            return
        }
        publishProxySyncFailure(error)
        logger.warning("sync: Ledger wallet failed to sync", error)
    }

    // The app's own lifecycle interrupting a sync is no news to the user; a yield to their operation is.
    private fun onProxySyncPutOff(error: Exception, stateBefore: AdapterState) {
        activeReconciliationSession()?.let { setSyncStateForSession(it, stateBefore) }
        if (error is LedgerSessionYieldedException && proxySyncEarlyEnd != ProxySyncEarlyEnd.Interruption) {
            ledgerUserOperations.reportYielded(LEDGER_MONERO_APP)
        }
    }

    private fun publishProxySyncFailure(error: Throwable) {
        _spendReadiness.value = MoneroSpendReadiness.Syncing
        _syncState.value = AdapterState.NotSynced(error)
    }

    /** The native refresh thread never runs for a Ledger: each trigger syncs inside a device session. */
    private suspend fun syncStartedProxyWallet(): Boolean {
        if (!isNetworkOnline) return false
        syncProxyWallet()
        return true
    }

    private fun syncProxyWalletLeaseOwned() {
        val session = requireActiveReconciliationSession()
        val height = refreshProxyWalletLeaseOwned()
        setLastBlockInfoForSession(session, LastBlockInfo(height.toInt()))
        emitTransactionsStateUpdatedForSession(session)
        setSpendReadinessForSession(session, MoneroSpendReadiness.Ready)
        setSyncStateForSession(session, AdapterState.Synced)
        refreshSubaddressesSnapshot()
    }

    /** Runs while the Ledger channel is registered; returns the synced height. */
    internal open fun refreshProxyWalletLeaseOwned(): Long {
        val wallet = requireNotNull(moneroWalletService.wallet) { "Monero Ledger wallet is not initialized" }
        val rescanHeight = restoreSettingsManager.settings(account, BlockchainType.Monero).birthdayHeight
            ?.takeIf { it >= 0 && it != wallet.restoreHeight }
        rescanHeight?.let {
            wallet.restoreHeight = it
            restoreHeightUnstored = true
            rescanObservedFor = null
            wallet.rescanBlockchainAsync()
        }
        val restoreHeight = wallet.restoreHeight
        refreshHardwareKeyImagesWithProgress(
            cachedTotalHeight = moneroWalletService.daemonHeight,
            fallbackTotalHeight = { wallet.daemonBlockChainHeight },
            onNewBlock = {
                if (restoreHeightUnstored) rescanObservedFor = restoreHeight
                yieldToLedgerUser { wallet.pauseRefreshAndDrain() }
            },
        ) {
            yieldBeforeRefreshIfRequested(wallet)
            if (!wallet.refresh()) throw wallet.fullStatus.hardwareFailure()
        }
        val height = wallet.blockChainHeight
        if (!moneroScanReachedDaemon(height, wallet.daemonBlockChainHeight, wallet.daemonBlockChainTargetHeight)) {
            storeEarlyEnd(wallet)
            // A checkpoint goes on in the same session; the birthday is read again, so a new rescan height applies.
            return refreshProxyWalletLeaseOwned()
        }
        wallet.setSynchronized()
        wallet.refreshHistory()
        // Periodic syncs mostly find nothing new; rewriting the cache then only wears the disk.
        if (restoreHeightUnstored || height != storedChainHeight) storeProxyWallet(wallet)
        checkpointRequested = false
        return height
    }

    // A refresh asked to end early stopped at a batch boundary, so what it scanned is kept.
    private fun storeEarlyEnd(wallet: Wallet) {
        val earlyEnd = proxySyncEarlyEnd ?: throw incompleteProxySyncError()
        storeProxyWallet(wallet)
        if (earlyEnd != ProxySyncEarlyEnd.Checkpoint) throw incompleteProxySyncError()
        proxySyncEarlyEnd = null
    }

    /**
     * Inside the device session: state a failed device may have corrupted never reaches the disk. A new restore
     * height reaches the keys only after the cache, and only once a block proved the rescan from it began.
     */
    private fun storeProxyWallet(wallet: Wallet) {
        ledgerGateway.requireIntactSession()
        storeHardwareWallet(wallet, "Failed to store Monero Ledger wallet")
        if (wallet.restoreHeightProven()) {
            storeHardwareWallet(wallet, "Failed to store Monero Ledger wallet keys", withKeys = true)
            restoreHeightUnstored = false
        }
        storedChainHeight = wallet.blockChainHeight
        checkpointRequested = false
    }

    private fun Wallet.restoreHeightProven() = restoreHeightUnstored && rescanObservedFor == restoreHeight

    // The close stores only the cache, so a restore height a scanned block proved goes to the keys first.
    private fun storeProvenRestoreHeight() {
        if (walletTainted || !restoreHeightUnstored) return
        moneroWalletService.wallet?.takeIf { it.restoreHeightProven() }?.let(::storeProxyWallet)
    }

    /**
     * Runs in wallet2's block callback on the refresh thread, which already holds the drain's wallet lock, so
     * [stopRefresh] returns at once and lands at the next batch. Must not throw back into the JNI callback.
     * Re-issued on every observed block (the observer passes one per 500 ms, so a stop may cost an extra batch
     * or two): a rescan runs a second wallet2 refresh, which resets an earlier stop.
     */
    internal fun yieldToLedgerUser(stopRefresh: () -> Unit) {
        val earlyEnd = requestedEarlyEnd() ?: return
        latchEarlyEnd(earlyEnd)
        stopRefresh()
    }

    private fun yieldBeforeRefreshIfRequested(wallet: Wallet) {
        when (val earlyEnd = requestedEarlyEnd()) {
            null -> Unit
            ProxySyncEarlyEnd.Checkpoint -> storeProxyWallet(wallet)
            else -> {
                latchEarlyEnd(earlyEnd)
                throw LedgerSessionYieldedException()
            }
        }
    }

    private fun requestedEarlyEnd(): ProxySyncEarlyEnd? = when {
        interruptionsWaiting.get() > 0 -> ProxySyncEarlyEnd.Interruption
        ledgerUserOperations.isActive -> ProxySyncEarlyEnd.UserYield
        checkpointRequested -> ProxySyncEarlyEnd.Checkpoint
        else -> null
    }

    private fun latchEarlyEnd(earlyEnd: ProxySyncEarlyEnd) {
        proxySyncEarlyEnd = maxOf(proxySyncEarlyEnd ?: earlyEnd, earlyEnd)
    }

    // The latched request, not the current user state: an operation that already ended still caused the stop.
    internal fun incompleteProxySyncError(): Exception = when (proxySyncEarlyEnd) {
        ProxySyncEarlyEnd.UserYield, ProxySyncEarlyEnd.Interruption -> LedgerSessionYieldedException()
        else -> HardwareWalletOperationException(HardwareWalletErrorCode.Network, "Monero node is not synchronized")
    }

    private fun resumeInitialHardwareCatchUp() {
        controlledLiveRefreshPending = false
        controlledLiveRefreshCommitted = false
        check(moneroWalletService.resume(this)) {
            "Failed to resume Monero wallet for initial synchronization"
        }
        isPaused = false
    }

    /** Runs while the lifecycle mutex and Trezor signer lease are both owned. */
    internal open fun refreshHardwareKeyImagesLeaseOwned(
        durableState: MoneroSpentReconciliationState,
        forcedMode: HardwareKeyImageRefreshResult.Mode? = null,
    ) {
        val wallet = requireNotNull(moneroWalletService.wallet) {
            "Monero hardware wallet is not initialized"
        }
        val restoreHeight = requireHardwareRestoreHeight()
        val pendingHeight = restoreSettingsManager.pendingMoneroRescanHeight(account)
        val mode = forcedMode ?: when {
            durableState == MoneroSpentReconciliationState.MigrationReplayPending ->
                HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight
            pendingHeight != null || wallet.restoreHeight != restoreHeight ->
                HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight
            else -> HardwareKeyImageRefreshResult.Mode.Continue
        }
        if (mode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight) {
            restoreSettingsManager.savePendingMoneroRescan(account, restoreHeight)
        }
        refreshHardwareKeyImagesWithProgress(
            cachedTotalHeight = moneroWalletService.daemonHeight,
            fallbackTotalHeight = { wallet.daemonBlockChainHeight },
            onNewBlock = {},
        ) {
            wallet.refreshWithHardwareKeyImages(
                HardwareKeyImageRefreshResult.Request(mode, restoreHeight),
            )
        }
        storeControlledHardwareRefresh(wallet, mode)
        if (mode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight) {
            restoreSettingsManager.clearPendingMoneroRescan(account)
        }
        if (durableState == MoneroSpentReconciliationState.MigrationReplayPending) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                account,
                MoneroSpentReconciliationState.LiveRefreshPending,
            )
        }
    }

    internal fun refreshHardwareKeyImagesWithProgress(
        cachedTotalHeight: Long,
        fallbackTotalHeight: () -> Long,
        onNewBlock: () -> Unit,
        refresh: () -> Unit,
    ) {
        val session = requireActiveReconciliationSession()
        val readTotalHeight = { tryOrNull(fallbackTotalHeight)?.takeIf { it > 0 } ?: 0 }
        var totalHeight = cachedTotalHeight.takeIf { it > 0 } ?: readTotalHeight()
        moneroWalletService.setControlledRefreshProgressObserver { height ->
            // Unknown before the first node answer: without it the row shows a bare "Syncing…".
            if (totalHeight == 0L) totalHeight = readTotalHeight()
            setSyncStateForSession(session, syncingState(height, totalHeight))
            onNewBlock()
        }
        try {
            refresh()
        } finally {
            moneroWalletService.clearControlledRefreshProgressObserver()
        }
    }

    private fun requireHardwareRestoreHeight(): Long =
        restoreSettingsManager.pendingMoneroRescanHeight(account)
            ?: restoreSettingsManager.settings(account, BlockchainType.Monero).birthdayHeight
            ?.takeIf { it >= 0 }
            ?: error("Monero hardware wallet has no restore height")

    private fun storeControlledHardwareRefresh(
        wallet: Wallet,
        mode: HardwareKeyImageRefreshResult.Mode,
    ) {
        val status = when (mode) {
            HardwareKeyImageRefreshResult.Mode.Continue -> wallet.storeSafe()
            HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight -> wallet.storeWithKeysSafe()
        }
        if (status == MONERO_STORE_OK) return
        val failure = HardwareWalletOperationException(
            HardwareWalletErrorCode.StoreFailed,
            "Failed to store controlled Monero Live Refresh",
        )
        if (status == MONERO_STORE_NATIVE_FAULT) {
            abandonFaultedWallet(failure)
        }
        throw failure
    }

    private fun abortControlledHardwareWallet(error: Throwable) {
        controlledLiveRefreshPending = false
        controlledLiveRefreshCommitted = false
        deactivateReconciliationSession()
        _syncState.value = AdapterState.NotSynced(error)
        try {
            if (!moneroWalletService.stop(false)) {
                moneroWalletService.abandonFaultedWallet()
            }
        } catch (cleanupError: Throwable) {
            error.addSuppressed(cleanupError)
            moneroWalletService.abandonFaultedWallet()
        }
        isStarted = false
        isPaused = false
        _spendReadiness.value = MoneroSpendReadiness.NeedsKeyImageSync
    }

    private suspend fun startService(
        walletFileName: String,
        walletPassword: String,
        fixIfCorruptedFile: Boolean
    ) {
        logger.info("startService: start walletFileName=$walletFileName fixIfCorruptedFile=$fixIfCorruptedFile")
        try {
            val walletStatus = moneroWalletService.start(walletFileName, walletPassword)
            logger.info(
                "startService: initial status=${walletStatus?.toString()} isOk=${walletStatus?.isOk} " +
                        "connection=${walletStatus?.connectionStatus}"
            )
            // Route EVERY start() result through the self-gated helper (incl. a successful Connected
            // start, which arms lastLoggedConnectionStatus so the first onRefreshed is not a "new" transition).
            recordNativeConnectionError(walletStatus?.connectionStatus, walletStatus?.errorString)
            if (walletStatus?.isOk != true) {
                logger.info("startService: wallet status not ok, scheduling retry")
                if (walletStatus?.connectionStatus == ConnectionStatus.ConnectionStatus_Disconnected) {
                    logger.info("startService: detected disconnected status, aborting start")
                    throw MoneroNodeUnreachableException()
                } else if (walletStatus == null) {
                    // Possible corrupted wallet file
                    getBirthdayHeight(account)?.let {
                        resetWalletAndRestart(it)
                    }
                } else {
                    retryStart(walletFileName, walletPassword)
                }
            }
        } catch (e: WalletCorruptedException) {
            logger.warning("startService: WalletCorruptedException received", e)
            handleWalletCorruption(e, walletFileName, walletPassword, fixIfCorruptedFile)
        } catch (e: Exception) {
            logger.warning("startService: unexpected exception", e)
            throw e
        }
    }

    private suspend fun handleWalletCorruption(
        error: WalletCorruptedException,
        walletFileName: String,
        walletPassword: String,
        fixIfCorruptedFile: Boolean
    ) {
        try {
            if (fixIfCorruptedFile) {
                if (error.message?.contains("std::bad_alloc") == true) { // too big cache file
                    val cacheFileSize = tryOrNull { getCacheFile().sizeInMb() } ?: ""
                    val deleted = tryOrNull { getCacheFile()?.delete() } ?: false
                    moneroLogger.d {
                        "MoneroKitManager: detected bad_alloc error(size: $cacheFileSize), deleted cache file: " +
                                "$deleted"
                    }
                    logger.info(
                        "startService: detected bad_alloc error(size: $cacheFileSize), deleting cache file, " +
                                "deleted=$deleted"
                    )
                    startService(walletFileName, walletPassword, false)
                    return
                }
                moneroLogger.e(error) {
                    "WalletCorruptedException, trying to fix wallet, cache size: " +
                            "${tryOrNull { getCacheFile().sizeInMb() } ?: "unknown"}"
                }
                logger.info("startService: attempting wallet fix after corruption")
                getBirthdayHeight(account)?.let {
                    resetWalletAndRestart(it)
                }
            } else {
                moneroLogger.e(error) { "WalletCorruptedException, fix disabled" }
                logger.info("startService: wallet fix disabled, corruption remains")
            }
        } catch (handlingError: Exception) {
            logger.warning("startService: failed while handling WalletCorruptedException", handlingError)
            moneroLogger.e(handlingError) { "Failed to fix corrupted wallet" }
        }
    }

    private suspend fun retryStart(walletFileName: String, walletPassword: String) {
        delay(3_000)
        val retryStatus = moneroWalletService.start(walletFileName, walletPassword)
        logger.info(
            "startService: retry status=${retryStatus?.toString()} isOk=${retryStatus?.isOk} " +
                    "connection=${retryStatus?.connectionStatus}"
        )
        recordNativeConnectionError(retryStatus?.connectionStatus, retryStatus?.errorString)
    }

    private fun getBirthdayHeight(account: Account): Long? {
        var birthdayHeight = restoreSettingsManager.settings(
            account,
            BlockchainType.Monero
        ).birthdayHeight
        if ((birthdayHeight ?: 0L) <= 0L) {
            birthdayHeight = (account.type as? AccountType.MnemonicMonero)?.height
        }
        return birthdayHeight
    }

    /**
     * @return true if wallet need to be fixed
     */
    private suspend fun fixCorruptedWalletFile(
        walletKeysFileName: String,
        walletPassword: String
    ) {
        logger.info("fixCorruptedWalletFile: check walletKeysFileName=$walletKeysFileName")
        if ((account.type as? AccountType.Mnemonic)?.kind != MnemonicKind.Mnemonic12) return

        if (WalletManager.getInstance()
                .verifyWalletPassword(walletKeysFileName, walletPassword, false)
        ) return

        val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
        moneroLogger.d { "Fixing corrupted wallet file with height: ${restoreSettings.birthdayHeight}" }
        logger.info("fixCorruptedWalletFile: fixing with restoreHeight=${restoreSettings.birthdayHeight}")
        restoreSettings.birthdayHeight?.let {
            resetWalletAndRestart(it)
        }
    }

    private suspend fun fixWalletHeight() {
        if (moneroWalletService.wallet?.restoreHeight != -1L ||
            (account.type as? AccountType.Mnemonic)?.kind != MnemonicKind.Mnemonic12
        ) return

        logger.info("fixWalletHeight: restoreHeight missing, resetting to validated height")
        // Use day of publishing this changes on google play as height
        // to fix possible first day of using this feature by users
        resetWalletAndRestart(validateMoneroHeightUseCase("2025-08-13"))
    }

    /**
     * Destructive rescan entry point exposed to callers that need to trigger it directly
     * (as opposed to the automatic corruption-recovery paths below, which call
     * [resetWalletAndRestart] from within a context that already holds [lifecycleMutex]).
     */
    suspend fun rescan(newHeight: Long) {
        when (deviceProtocol) {
            MoneroDeviceProtocol.Cold -> rescanHardwareWallet(newHeight)
            MoneroDeviceProtocol.Proxy -> rescanProxyWallet(newHeight)
            MoneroDeviceProtocol.None -> lifecycleMutex.withLock { resetWalletAndRestart(newHeight) }
        }
    }

    // The next sync applies the height to the open wallet, a running one at its next boundary; the files stay.
    private suspend fun rescanProxyWallet(newHeight: Long) {
        restoreSettingsManager.saveMoneroRestoreHeight(account, newHeight)
        requestCheckpoint()
        requestProxySync(ProxySyncPass.Refresh)
    }

    private suspend fun rescanHardwareWallet(newHeight: Long) {
        require(newHeight >= 0) { "Monero restore height must be non-negative" }
        lifecycleMutex.withLock {
            restoreSettingsManager.savePendingMoneroRescan(account, newHeight)
        }
        hardwareRescanPending = true
        try {
            _syncState.value = AdapterState.Syncing()
            refreshHardwareKeyImages(HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight)
        } finally {
            hardwareRescanPending = false
        }
    }

    suspend fun refreshHardwareKeyImages() {
        refreshHardwareKeyImages(forcedMode = null)
    }

    private suspend fun refreshHardwareKeyImages(
        forcedMode: HardwareKeyImageRefreshResult.Mode?,
    ) {
        controlledRefreshCoordinator.execute(
            object : ControlledHardwareRefreshOperation {
                override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
                    lifecycleMutex.withLock {
                        try {
                            val requiresFinalization =
                                refreshHardwareKeyImagesLocked(aborter, forcedMode)
                            if (requiresFinalization) {
                                awaitControlledHardwareRefreshFinalization()
                            }
                        } catch (error: Throwable) {
                            // Keep abort and native close inside lifecycle serialization. A queued
                            // stop/restart must not touch this wallet until discard completes.
                            aborter.abort(error)
                            throw error
                        }
                    }
                }

                override fun abort(error: Throwable) {
                    abortControlledHardwareWallet(error)
                }
            },
        )
    }

    private suspend fun refreshHardwareKeyImagesLocked(
        aborter: ControlledHardwareRefreshAborter,
        forcedMode: HardwareKeyImageRefreshResult.Mode?,
    ): Boolean {
        check(coldProtocol) { "Monero Live Refresh requires a hardware wallet" }
        val durableState = restoreSettingsManager.moneroSpentReconciliationState(account)
        val requiresControlledFinalization =
            durableState.requiresControlledRefreshFinalization()
        if (!requiresControlledFinalization) {
            resumeExplicitColdRecoveryLocked()
            return false
        }
        val refreshState = durableState.normalizedLiveRefreshState()
        restoreSettingsManager.saveMoneroSpentReconciliationState(account, refreshState)
        controlledLiveRefreshPending = true
        controlledLiveRefreshCommitted = false
        val credentials = if (moneroWalletService.wallet == null) resolveWalletCredentials() else null
        moneroHardwareGateway.execute(account) {
            try {
                credentials?.let {
                    openPausedHardwareWalletForRefresh {
                        startHardwareServicePaused(it.fileName, it.password)
                    }
                }
                val wallet = requireNotNull(moneroWalletService.wallet) {
                    "Monero hardware wallet is not initialized"
                }
                if (!isPaused) {
                    pauseAndDrain(wallet)
                }
                refreshHardwareKeyImagesLeaseOwned(refreshState, forcedMode)
                controlledLiveRefreshCommitted = true
            } catch (error: Throwable) {
                aborter.abort(error)
                throw error
            }
        }
        return true
    }

    private suspend fun resumeExplicitColdRecoveryLocked() {
        val credentials = if (moneroWalletService.wallet == null) resolveWalletCredentials() else null
        credentials?.let {
            openPausedHardwareWalletForRefresh {
                startHardwareServicePaused(it.fileName, it.password)
            }
        }
        val wallet = requireNotNull(moneroWalletService.wallet) {
            "Monero hardware wallet is not initialized"
        }
        if (!isPaused) pauseAndDrain(wallet)
        syncKeyImagesLocked()
    }

    internal fun openPausedHardwareWalletForRefresh(start: () -> Unit) {
        val previousStartInProgress = startInProgress
        startInProgress = true
        try {
            activateReconciliationSession()
            start()
            isStarted = true
            isPaused = true
            refreshSubaddressesSnapshot()
        } finally {
            startInProgress = previousStartInProgress
        }
    }

    private suspend fun resetWalletAndRestart(birthdayHeight: Long) {
        logger.info("resetWalletAndRestart: requested with birthdayHeight=$birthdayHeight")
        val fileName = requireMoneroWalletFileName()

        stopInternal(false)

        val removed = removeMoneroWalletFilesUseCase(fileName)
        if (!removed) {
            failRescanAfterRemoval(fileName)
        }

        moneroFileDao.deleteAssociatedRecord(account.id)

        val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
        restoreSettings.birthdayHeight = birthdayHeight
        restoreSettingsManager.save(restoreSettings, account, BlockchainType.Monero)

        startInternal(fixIfCorruptedFile = false)
        // startInternal swallows startup/derive failures into AdapterState.NotSynced and leaves
        // isStarted=false. Surface that as a hard rescan failure so callers don't report success
        // after the destructive reset failed to restart the wallet. (Transient "not synced yet"
        // still has isStarted=true, so a normal offline start is not treated as a failure.)
        if (!isStarted) {
            failRescanAfterRestart()
        }
        logger.info("resetWalletAndRestart: restart complete")
    }

    private suspend fun requireMoneroWalletFileName(): String =
        getMoneroWalletFilesNameUseCase(account)
            ?: throw MoneroRescanException("No Monero wallet file found for account ${account.id}")

    private suspend fun failRescanAfterRemoval(fileName: String): Nothing {
        logger.info("resetWalletAndRestart: failed to remove walletFile=$fileName, rolling back")
        startInternal(fixIfCorruptedFile = false)
        throw MoneroRescanException("Failed to remove Monero wallet file $fileName")
    }

    private fun failRescanAfterRestart(): Nothing {
        throw MoneroRescanException(
            "Failed to restart Monero wallet after rescan for account ${account.id}",
        )
    }

    suspend fun stop(saveWallet: Boolean = true) = withLifecycleLock(LifecycleInterruption.Stop) {
        stopInternal(saveWallet)
    }

    /** The manager's close on an account switch or unlink: this wrapper never opens its wallet again. */
    suspend fun close() = withLifecycleLock(LifecycleInterruption.Stop) {
        stopInternal()
        retired = true
    }

    internal suspend fun <T> withNativeWalletReleased(
        localOnly: Boolean,
        block: suspend () -> T,
    ): T = withLifecycleLock(LifecycleInterruption.Preempt) {
        val restart = isStarted
        if (restart) {
            withContext(NonCancellable) { stopInternal() }
        }
        val result = try {
            block()
        } catch (error: Throwable) {
            if (restart) {
                restoreNativeWalletFailure(localOnly)?.let(error::addSuppressed)
            }
            throw error
        }
        if (restart) {
            restoreNativeWalletFailure(localOnly)?.let { throw it }
        }
        result
    }

    private suspend fun restoreNativeWalletFailure(localOnly: Boolean): Throwable? =
        try {
            withContext(NonCancellable) {
                startInternal(localOnly = localOnly)
                check(isStarted) { "Failed to restore the active Monero wallet" }
            }
            null
        } catch (error: Throwable) {
            error
        }

    private suspend fun stopInternal(saveWallet: Boolean = true) = withContext(Dispatchers.IO) {
        // Invalidate before the native close starts. A refresh callback can arrive while close()
        // is blocking, but it must not be able to attach reconciliation work to this session.
        deactivateReconciliationSession()
        try {
            if (isStarted) {
                logger.info("stop: stopping service saveWallet=$saveWallet")
                if (saveWallet) storeProvenRestoreHeight()
                val closed = try {
                    nativeCloseSequence++
                    moneroWalletService.stop(saveWallet && !walletTainted)
                } finally {
                    nativeCloseSequence++
                }
                if (hardwareWallet && !closed) {
                    isPaused = true
                    throw moneroWalletService.wallet?.status.toHardwareWalletCloseFailure(
                        "Failed to close Monero hardware wallet",
                    )
                }
                isStarted = false
                isPaused = false
                openedWithoutNode = false
                restoreHeightUnstored = false
                rescanObservedFor = null
                lastLoggedSyncProgress = -1
                lastLoggedConnectionStatus = null
                if (hardwareWallet) {
                    _spendReadiness.value = MoneroSpendReadiness.Syncing
                }
                logger.info("stop: service stopped")
            } else {
                logger.info("stop: skip, service already stopped")
            }
        } finally {
            // Drain a callback that was already in flight before the first cancellation.
            deactivateReconciliationSession()
        }
    }

    suspend fun pause() = withLifecycleLock(LifecycleInterruption.Preempt) {
        if (isStarted && !isPaused) {
            logger.info("pause: pausing wallet refresh")
            moneroWalletService.pause()
            isPaused = true
            logger.info("pause: done")
        } else {
            logger.info("pause: skip, isStarted=$isStarted isPaused=$isPaused")
        }
    }

    // A Ledger sync opens the wallet itself when it is closed, so the caller never has to start it.
    suspend fun resume(): Boolean {
        if (!proxyProtocol) return lifecycleMutex.withLock { resumeAttachingNode() }
        requestProxySync(ProxySyncPass.Refresh)
        return true
    }

    // A failed connect still initializes the daemon, so a local resume would look online without a node.
    private suspend fun resumeAttachingNode(): Boolean =
        if (openedWithoutNode) connectDaemonAndResume() else resumeInternal()

    val isNetworkOnline: Boolean
        get() = isStarted && !isPaused

    suspend fun resumeNetwork(): Boolean = withLifecycleLock(LifecycleInterruption.Preempt) {
        if (!isStarted || !isPaused) return@withLifecycleLock isStarted
        if (hardwareWallet) {
            stopInternal(saveWallet = false)
            try {
                startInternal()
            } catch (error: Throwable) {
                restoreNativeWalletFailure(localOnly = true)?.let(error::addSuppressed)
                _spendReadiness.value = MoneroSpendReadiness.ReconciliationFailed
                throw error
            }
            return@withLifecycleLock isNetworkOnline
        }
        connectDaemonAndResume()
    }

    private suspend fun connectDaemonAndResume(): Boolean = withContext(dispatcherProvider.io) {
        daemonSelector.selectDaemon()
        moneroWalletService.setObserver(this@MoneroKitWrapper)
        moneroWalletService.connectDaemon() is DaemonConnectResult.Connected && resumeInternal()
    }

    private fun resumeInternal(): Boolean {
        if (isStarted && isPaused) {
            logger.info("resume: resuming wallet refresh")
            val resumed = moneroWalletService.resume(this)
            if (resumed) {
                isPaused = false
                openedWithoutNode = false
                logger.info("resume: done")
            } else {
                logger.info("resume: service resume returned false")
            }
            return resumed
        } else {
            logger.info("resume: skip, isStarted=$isStarted isPaused=$isPaused")
            return false
        }
    }

    /**
     * Persists the wallet cache via a SIGSEGV-guarded native store, once per session,
     * the first time sync reaches [AdapterState.Synced]. Runs OFF the refresh thread
     * (dispatcherProvider.io) and is serialized with stop()/pause()/resume()/send() via
     * lifecycleMutex, so it never races a concurrent native call on the wallet.
     */
    suspend fun saveSynced(): Boolean {
        // A Ledger wallet stores at the end of each sync, inside its device session.
        if (proxyProtocol) return storedForSync
        return lifecycleMutex.withLock { saveSyncedLocked() }
    }

    private suspend fun saveSyncedLocked(): Boolean =
        withContext(dispatcherProvider.io) {
            if (!shouldSaveSyncedWallet()) {
                return@withContext storedForSync
            }
            var status = -1
            try {
                moneroWalletService.pause()
                delay(200)
                var attempt = 0
                while (attempt < 2) {
                    status = moneroWalletService.wallet?.storeSafe() ?: -1
                    if (status != 1) break
                    attempt++
                    delay(200)
                }
                when (status) {
                    MONERO_STORE_OK -> {
                        if (coldProtocol) {
                            restoreSettingsManager.clearPendingMoneroRescan(account)
                        }
                        storedForSync = true
                        logger.info("saveSynced: stored at height=${moneroWalletService.wallet?.blockChainHeight}")
                    }

                    MONERO_STORE_NATIVE_FAULT -> {
                        val failure = HardwareWalletOperationException(
                            HardwareWalletErrorCode.StoreFailed,
                            "Failed to store synchronized Monero wallet",
                        )
                        abandonFaultedWallet(failure)
                        logger.warning("saveSynced: SIGSEGV, wallet abandoned", failure)
                    }

                    else -> logger.info("saveSynced: storeSafe status=$status")
                }
            } finally {
                if (status == MONERO_STORE_NATIVE_FAULT) {
                    recoverFaultedWallet()?.let {
                        logger.warning("saveSynced: failed to reopen faulted wallet", it)
                    }
                } else {
                    moneroWalletService.resume(this@MoneroKitWrapper)
                }
            }
            storedForSync
        }

    private fun shouldSaveSyncedWallet(): Boolean =
        isStarted &&
            !isPaused &&
            !storedForSync &&
            _syncState.value is AdapterState.Synced

    suspend fun onDeviceAttached() {
        if (proxyProtocol) requestProxySync(ProxySyncPass.Attach)
    }

    /** New blocks reach a Ledger wallet only through the device, so an open wallet catches up while its app is open. */
    suspend fun onLedgerAppOpen() {
        if (proxyProtocol) requestProxySync(ProxySyncPass.SyncStarted)
    }

    suspend fun refresh() {
        if (proxyProtocol) requestProxySync(ProxySyncPass.Refresh) else lifecycleMutex.withLock { refreshLocked() }
    }

    /** Requests nothing more than a store at the running Ledger sync's next batch boundary; it then goes on. */
    suspend fun requestCheckpoint() = proxySyncLock.withLock {
        if (proxySync?.isCompleted == false) checkpointRequested = true
    }

    // A trigger arriving while a sync runs, or while an interruption is pending, is coalesced into one later pass.
    private suspend fun requestProxySync(pass: ProxySyncPass) {
        proxySyncLock.withLock {
            pendingProxySyncPass = maxOf(pendingProxySyncPass ?: pass, pass)
            takePendingProxySync()
        }?.start()
    }

    // Under proxySyncLock, by whoever ends last: the previous sync or the interruption. Started after the lock.
    private fun takePendingProxySync(): Job? {
        val pass = pendingProxySyncPass ?: return null
        if (proxySync?.isCompleted == false || pendingInterruptions > 0) return null
        pendingProxySyncPass = null
        return proxySyncScope.launch(start = CoroutineStart.LAZY) { runProxySync(pass) }.also { proxySync = it }
    }

    private suspend fun runProxySync(pass: ProxySyncPass) {
        val self = currentCoroutineContext().job
        try {
            lifecycleMutex.withLock { runProxySyncPass(pass) }
        } finally {
            withContext(NonCancellable) {
                proxySyncLock.withLock {
                    if (proxySync === self) proxySync = null
                    takePendingProxySync()
                }
            }?.start()
        }
    }

    private suspend fun runProxySyncPass(pass: ProxySyncPass) {
        when (pass) {
            ProxySyncPass.SyncStarted -> if (isStarted) refreshLocked()
            ProxySyncPass.Attach -> refreshAttachedDevice()
            ProxySyncPass.Refresh -> refreshLocked()
        }
    }

    /**
     * A Ledger plugged in syncs the open wallet, or retries an open that failed for lack of a device; an open
     * the user refused stays closed. App switches re-plug the device too, so this also runs after own operations;
     * an open wallet therefore syncs only if the Monero app is already open, never switching away from another one.
     */
    private suspend fun refreshAttachedDevice() {
        val failedWithoutDevice = (_syncState.value as? AdapterState.NotSynced)?.error?.isLedgerUnreachable == true
        when {
            isStarted -> withContext(LedgerAppSwitchForbidden) { refreshLocked() }
            failedWithoutDevice -> refreshLocked()
        }
    }

    /**
     * Takes [lifecycleMutex] for anything but the Ledger sync: a running sync ends at its next batch boundary and a
     * queued one is cancelled before it reaches wallet2 (its scan never sees the cancellation). A preempted sync
     * runs once more afterwards; a stopped one does not.
     */
    private suspend fun <T> withLifecycleLock(interruption: LifecycleInterruption, block: suspend () -> T): T {
        if (!proxyProtocol) return lifecycleMutex.withLock { block() }
        val preempted = proxySyncLock.withLock {
            pendingInterruptions++
            interruptionsWaiting.incrementAndGet()
            proxySync?.takeIf { !it.isCompleted }
        }
        try {
            lockInterruptingSync(preempted)
            try {
                return block()
            } finally {
                lifecycleMutex.unlock()
            }
        } finally {
            withContext(NonCancellable) { endInterruption(interruption, preempted) }?.start()
        }
    }

    // Only while waiting: a sync the holder itself runs, e.g. a reopen, must not end early.
    private suspend fun lockInterruptingSync(preempted: Job?) {
        try {
            preempted?.cancelAndJoin()
            lifecycleMutex.lock()
        } finally {
            interruptionsWaiting.decrementAndGet()
        }
    }

    private suspend fun endInterruption(interruption: LifecycleInterruption, preempted: Job?) = proxySyncLock.withLock {
        pendingInterruptions--
        if (interruption == LifecycleInterruption.Stop) {
            pendingProxySyncPass = null
        } else if (preempted != null) {
            pendingProxySyncPass = pendingProxySyncPass ?: ProxySyncPass.SyncStarted
        }
        takePendingProxySync()
    }

    private suspend fun refreshLocked() {
        // A Ledger sync is guarded by its job; the state it shows may be one a put-off left behind.
        if (!proxyProtocol && _syncState.value is AdapterState.Syncing) {
            logger.info("refresh: skip, already syncing")
            moneroLogger.d { "MoneroKitWrapper: Already syncing, skipping refresh" }
            return
        }
        try {
            if (isStarted) {
                refreshStartedWallet()
            } else {
                logger.info("refresh: starting wallet")
                startInternal()
            }
        } catch (e: Exception) {
            logger.warning("refresh: failed to refresh wallet", e)
            moneroLogger.e(e) { "Failed to refresh Monero wallet" }
        }
    }

    private suspend fun refreshStartedWallet() {
        if (proxyProtocol) {
            syncStartedProxyWallet()
            return
        }
        if (isPaused && !resumeAttachingNode()) {
            if (openedWithoutNode) {
                logger.info("refresh: node unreachable, wallet stays open locally")
                return
            }
            logger.info("refresh: restarting wallet after failed resume")
            restartInternal()
            return
        }

        if (requestWalletRefresh()) {
            logger.info("refresh: requesting wallet refresh")
        } else {
            logger.info("refresh: restarting wallet")
            restartInternal()
        }
    }

    private suspend fun restartInternal() {
        stopInternal(saveWallet = false)
        startInternal()
    }

    private fun requestWalletRefresh(): Boolean {
        val wallet = moneroWalletService.wallet ?: return false
        wallet.refreshAsync()
        return true
    }

    // Held under lifecycleMutex so a background stop() cannot close the wallet mid-send.
    suspend fun send(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): String = asLedgerUserOperation {
        lifecycleMutex.withLock {
            withContext(Dispatchers.IO) {
                if (proxyProtocol) {
                    sendProxy(amount, address, memo)
                } else if (coldProtocol) {
                    // This intentionally touches no native wallet state: do it before pause/drain
                    // and before acquiring a hardware-device session.
                    requireHardwareSendLifecycleReady()
                    val wallet = moneroWalletService.wallet
                        ?: throw IllegalStateException("Monero wallet not initialized")
                    sendHardware(wallet, amount, address, memo)
                } else {
                    prepareAndSendTransaction(amount, address, memo)
                }
            }
        }
    }

    // Marked before the lock, so a running Ledger sync gives the device up instead of finishing first.
    private suspend fun <T> asLedgerUserOperation(block: suspend () -> T): T =
        if (proxyProtocol) ledgerUserOperations.run(block) else block()

    internal open fun prepareAndSendTransaction(amount: BigDecimal, address: String, memo: String?): String {
        val wallet = moneroWalletService.wallet ?: throw IllegalStateException("Monero wallet not initialized")
        moneroWalletService.prepareTransaction(buildTxData(amount, address, memo, wallet))
        return moneroWalletService.sendTransaction(memo)
    }

    private suspend fun sendHardware(
        wallet: Wallet,
        amount: BigDecimal,
        address: String,
        memo: String?,
    ): String {
        return withPausedHardwareWallet(
            wallet = wallet,
            block = {
                var prepared = false
                try {
                    moneroHardwareGateway.execute(account) {
                        requireHardwareSendReady(wallet)
                        moneroWalletService.prepareTransaction(buildTxData(amount, address, memo, wallet))
                        prepared = true
                    }
                    requireHardwareSendReady(wallet)
                    moneroWalletService.sendTransaction(memo)
                } catch (error: Throwable) {
                    if (prepared) {
                        try {
                            wallet.disposePendingTransaction()
                        } catch (cleanupError: Throwable) {
                            error.addSuppressed(cleanupError)
                        }
                    }
                    throw error
                }
            },
            preserveResultOnResumeFailure = true,
        )
    }

    // The Ledger signs inside createTransaction, so its channel stays registered for the whole call.
    private suspend fun sendProxy(amount: BigDecimal, address: String, memo: String?): String {
        check(isNetworkOnline && _spendReadiness.value == MoneroSpendReadiness.Ready) {
            "Monero Ledger wallet is not ready to send"
        }
        return moneroHardwareGateway.execute(account) { prepareAndSendTransaction(amount, address, memo) }
    }

    suspend fun syncKeyImages() = withLifecycleLock(LifecycleInterruption.Preempt) {
        syncKeyImagesLocked()
    }

    private suspend fun syncKeyImagesLocked() {
        withContext(dispatcherProvider.io) {
            check(coldProtocol) { "Key image sync requires a hardware wallet" }
            val session = requireActiveReconciliationSession()
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            keyImageSyncSession = session
            try {
                // This write is the crash boundary: a process death at any later point must make
                // the next start reconcile rather than exposing imported key images as spendable.
                restoreSettingsManager.saveMoneroSpentReconciliationState(
                    account,
                    MoneroSpentReconciliationState.ExplicitColdRecoveryPending,
                )
                setSpendReadinessForSession(session, MoneroSpendReadiness.CheckingKeyImages)
                val result = withPausedHardwareWallet(wallet, block = {
                    moneroHardwareGateway.execute(account) {
                        wallet.coldKeyImageSync().also {
                            // Keep this inside the gateway operation.  The Trezor operation must
                            // not be released before the imported key images are durable.
                            storeHardwareWallet(
                                wallet,
                                "Failed to store synchronized Monero key images",
                            )
                        }
                    }
                })

                when (coldKeyImageSyncNextStep(result.spentStatusVerified, wallet.hasUnknownKeyImages())) {
                    ColdKeyImageSyncNextStep.TrustedReady -> finalizeTrustedKeyImageSync(session, wallet)
                    ColdKeyImageSyncNextStep.PreserveKeyImagesRescan -> requestSpentReconciliation(session, wallet)
                    ColdKeyImageSyncNextStep.NeedsKeyImageSync -> {
                        clearReconciliationOperation(session)
                        setSpendReadinessForSession(session, MoneroSpendReadiness.NeedsKeyImageSync)
                    }
                }
            } catch (error: Throwable) {
                failClosedAfterReconciliationError(session, error)
                throw error
            }
        }
    }

    suspend fun displayAddressOnDevice(addressIndex: Int) = asLedgerUserOperation {
        lifecycleMutex.withLock {
            withContext(dispatcherProvider.io) {
                require(addressIndex >= 0) { "Monero address index must be non-negative" }
                check(hardwareWallet) { "Address display requires a hardware wallet" }
                val wallet = moneroWalletService.wallet
                    ?: throw IllegalStateException("Monero wallet not initialized")
                val showAddress = suspend {
                    moneroHardwareGateway.execute(account) {
                        wallet.deviceShowAddress(0, addressIndex, "")
                    }
                }
                if (proxyProtocol) showAddress() else withPausedHardwareWallet(wallet, block = showAddress)
            }
        }
    }

    private fun pauseAndDrain(wallet: Wallet) {
        moneroWalletService.pause()
        isPaused = true
        try {
            check(wallet.pauseRefreshAndDrain()) { "Failed to drain Monero refresh" }
        } catch (error: Throwable) {
            if (!moneroWalletService.resume(this)) {
                error.addSuppressed(
                    IllegalStateException("Failed to resume Monero wallet after drain failure"),
                )
            } else {
                isPaused = false
            }
            throw error
        }
    }

    // wallet2 keeps the restore height only in the keys file; a cache-only store loses a changed one.
    private fun storeHardwareWallet(wallet: Wallet, failureMessage: String, withKeys: Boolean = false) {
        storeMoneroWalletSafely(
            failureMessage = failureMessage,
            store = if (withKeys) wallet::storeWithKeysSafe else wallet::storeSafe,
            onNativeFault = ::abandonFaultedWallet,
        )
    }

    private fun abandonFaultedWallet(failure: HardwareWalletOperationException) {
        deactivateReconciliationSession()
        moneroWalletService.abandonFaultedWallet()
        isStarted = false
        isPaused = false
        nativeStoreFaulted = true
        _syncState.value = AdapterState.NotSynced(failure)
        if (hardwareWallet) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    private suspend fun recoverFaultedWallet(): Throwable? {
        nativeStoreFaulted = false
        return try {
            startInternal(fixIfCorruptedFile = true)
            null
        } catch (error: Throwable) {
            error
        }
    }

    private fun resumeAfterHardwareOperation() {
        if (!isStarted) return
        check(moneroWalletService.resume(this)) {
            "Failed to resume Monero wallet after hardware operation"
        }
        isPaused = false
    }

    private suspend fun awaitControlledHardwareRefreshFinalization() {
        val finalization = CompletableDeferred<Unit>()
        check(controlledRefreshFinalization == null) {
            "Monero controlled refresh finalization is already pending"
        }
        controlledRefreshFinalization = finalization
        try {
            check(moneroWalletService.resumeAfterControlledRefresh(this)) {
                "Failed to resume Monero wallet after controlled refresh"
            }
            awaitControlledRefreshFinalization(
                finalization,
                CONTROLLED_REFRESH_FINALIZATION_TIMEOUT_MILLIS,
            )
        } finally {
            if (controlledRefreshFinalization === finalization) {
                controlledRefreshFinalization = null
            }
        }
        isPaused = false
    }

    private suspend fun <T> withPausedHardwareWallet(
        wallet: Wallet,
        block: suspend () -> T,
        preserveResultOnResumeFailure: Boolean = false,
    ): T {
        try {
            pauseAndDrain(wallet)
        } catch (error: Throwable) {
            failHardwarePause(error)
        }
        val result = try {
            block()
        } catch (error: Throwable) {
            failHardwareOperation(error)
        }
        return completeHardwareOperation(
            result,
            resumeHardwareWalletFailure(),
            preserveResultOnResumeFailure,
        )
    }

    internal fun <T> completeHardwareOperation(
        result: T,
        resumeFailure: Throwable?,
        preserveResultOnResumeFailure: Boolean,
    ): T {
        resumeFailure?.let { handleHardwareResumeFailure(it, preserveResultOnResumeFailure) }
        return result
    }

    private fun failHardwarePause(error: Throwable): Nothing {
        if (isPaused) {
            recordHardwareRecoveryFailure(error)
        }
        throw error
    }

    private suspend fun failHardwareOperation(error: Throwable): Nothing {
        if (nativeStoreFaulted) {
            if (startInProgress) {
                nativeStoreFaulted = false
            } else {
                withContext(NonCancellable) {
                    recoverFaultedWallet()
                }?.let(error::addSuppressed)
            }
            throw error
        }
        resumeHardwareWalletFailure()?.let { resumeFailure ->
            recordHardwareRecoveryFailure(resumeFailure)
            error.addSuppressed(resumeFailure)
        }
        throw error
    }

    private fun handleHardwareResumeFailure(
        error: Throwable,
        preserveResult: Boolean,
    ) {
        recordHardwareRecoveryFailure(error)
        if (!preserveResult) {
            throw error
        }
    }

    private fun recordHardwareRecoveryFailure(error: Throwable) {
        _syncState.value = AdapterState.NotSynced(error)
        _spendReadiness.value = MoneroSpendReadiness.Syncing
        moneroLogger.e(error) { "Failed to recover Monero wallet after hardware operation" }
    }

    private fun resumeHardwareWalletFailure(): Throwable? =
        try {
            resumeAfterHardwareOperation()
            null
        } catch (error: Throwable) {
            error
        }

    // Held under lifecycleMutex so a background stop()/saveSynced() cannot race a raw-tx sign/submit.
    suspend fun createSignedRawTransaction(
        amount: BigDecimal,
        address: String,
        memo: String?,
    ): SignedRawMoneroTransaction = withLifecycleLock(LifecycleInterruption.Preempt) {
        withContext(Dispatchers.IO) {
            check(!hardwareWallet) { "Offline signing is unavailable for hardware Monero wallets" }
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            moneroWalletService.createSignedRawTransaction(buildTxData(amount, address, memo, wallet))
        }
    }

    // Held under lifecycleMutex so a background stop()/saveSynced() cannot race a raw-tx sign/submit.
    suspend fun submitSignedRawTransaction(raw: ByteArray): RawMoneroBroadcastResult =
        withLifecycleLock(LifecycleInterruption.Preempt) {
            withContext(Dispatchers.IO) {
                if (coldProtocol) {
                    requireHardwareSendLifecycleReady()
                    val wallet = moneroWalletService.wallet
                        ?: throw IllegalStateException("Monero wallet not initialized")
                    withPausedHardwareWallet(
                        wallet = wallet,
                        block = {
                            requireHardwareSendReady(wallet, requireCurrentWallet = true)
                            moneroWalletService.submitSignedRawTransaction(raw)
                        },
                        preserveResultOnResumeFailure = true,
                    )
                } else {
                    moneroWalletService.submitSignedRawTransaction(raw)
                }
            }
        }

    /**
     * This is deliberately called inside [lifecycleMutex] at each hardware transaction boundary.
     * Reading all state together makes a stale Ready value insufficient to authorize spending.
     */
    private fun requireHardwareSendLifecycleReady() {
        check(isStarted && activeReconciliationSession() != null) {
            "Monero hardware wallet session is not active"
        }
        check(
            isHardwareSpendLifecycleReady(
                syncState = _syncState.value,
                durableState = restoreSettingsManager.moneroSpentReconciliationState(account),
                spendReadiness = _spendReadiness.value,
            ),
        ) { "Monero hardware wallet is not ready to send" }
    }

    private fun requireNoUnknownHardwareKeyImages(wallet: Wallet) {
        check(!wallet.hasUnknownKeyImages()) { "Monero hardware wallet has unknown key images" }
    }

    private fun requireHardwareSendReady(wallet: Wallet, requireCurrentWallet: Boolean = false) {
        requireHardwareSendLifecycleReady()
        if (requireCurrentWallet) {
            check(moneroWalletService.wallet === wallet) { "Monero hardware wallet changed before sending" }
        }
        check(walletHealthReader.snapshot(wallet).isFullyHealthy) {
            "Monero hardware wallet native health changed before sending"
        }
        // Keep native checks at the transaction boundary, after JVM-only gates and health read.
        requireNoUnknownHardwareKeyImages(wallet)
    }

    // Held under lifecycleMutex so a background stop() cannot close the wallet mid-estimate.
    suspend fun estimateFee(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): Long = withLifecycleLock(LifecycleInterruption.Preempt) {
        val wallet = moneroWalletService.wallet
            ?: throw IllegalStateException("Monero wallet not initialized")
        val txData = buildTxData(amount, address, memo, wallet)
        val fee = wallet.estimateTransactionFee(txData)
        if (fee < 0) {
            throw IllegalStateException("Failed to estimate fee: wallet not synced with daemon")
        }
        fee
    }

    private fun buildTxData(
        amount: BigDecimal,
        address: String,
        memo: String?,
        wallet: Wallet
    ) = TxData().apply {
        this.destination = address
        this.amount = amount.movePointRight(MoneroAdapter.decimal).toLong()
        this.mixin = wallet.defaultMixin
        this.priority = PendingTransaction.Priority.Priority_Default
        memo?.let {
            this.userNotes = UserNotes(it)
        }
    }

    fun statusInfo(): Map<String, Any> {
        logger.info(
            "statusInfo: connection=${moneroWalletService.connectionStatus} " +
                    "wallet=${moneroWalletService.wallet?.status} isStarted=$isStarted " +
                    "restoreHeight=${moneroWalletService.wallet?.restoreHeight}"
        )
        val base = mapOf<String, Any>(
            "connectionStatus" to moneroWalletService.connectionStatus,
            "walletStatus" to moneroWalletService.wallet?.status?.toString().orEmpty(),
            "isStarted" to isStarted,
            "Birthday Height" to (getBirthdayHeight(account) ?: "Not set"),
            "Cache file size" to (tryOrNull { getCacheFile().sizeInMb() } ?: "")
        )
        return networkErrorTracker.appendNetworkErrors(base, BlockchainType.Monero, account.id)
    }

    /**
     * Records a native (non-OkHttp) connection error into the shared tracker. Self-gated on
     * [lastLoggedConnectionStatus] so repeated same-status callbacks record once; ignores null and
     * Connected. Passive: never throws into the caller.
     */
    private fun recordNativeConnectionError(status: ConnectionStatus?, errorString: String?) {
        val s = status ?: return
        if (s == lastLoggedConnectionStatus) return
        lastLoggedConnectionStatus = s
        if (s == ConnectionStatus.ConnectionStatus_Connected) return
        tryOrNull {
            val addr = tryOrNull { WalletManager.getInstance().getDaemonAddress() }.orEmpty()
            networkErrorTracker.record(
                BlockchainType.Monero,
                account.id,
                NetworkErrorInfo(
                    source = "Monero",
                    method = s.name,
                    url = addr,
                    host = addr,
                    resolvedIps = emptyList(),
                    throwable = IllegalStateException(
                        errorString?.takeIf { it.isNotBlank() } ?: "Not connected"
                    ),
                )
            )
        }
    }

    private fun getCacheFile(): File? {
        return walletFileNameForStatus?.let { File(moneroWalletStorage.walletsRoot(), it) }
    }

    // Add methods for balance, transactions, etc.
    fun getBalance(): Long? {
        // Odd sequence = closing: the kit then reads 0 instead of failing, so an overlapping close voids the read.
        val closeSequence = nativeCloseSequence
        if (!isStarted || closeSequence % 2 != 0) return null
        val balance = try {
            moneroWalletService.wallet?.balance
        } catch (e: Exception) {
            logger.warning("getBalance: failed to fetch balance", e)
            null
        }
        return balance.takeIf { nativeCloseSequence == closeSequence }
    }

    fun getUnlockedBalance(): Long {
        return try {
            moneroWalletService.wallet?.unlockedBalance ?: 0L
        } catch (e: Exception) {
            logger.warning("getUnlockedBalance: failed to fetch unlocked balance", e)
            0L
        }
    }

    fun getAddress(): String {
        return try {
            requireNotNull(moneroWalletService.wallet).address
        } catch (e: Exception) {
            logger.warning("getAddress: failed to fetch address", e)
            moneroLogger.d { "getAddress: Failed to get address $e" }
            ""
        }
    }

    suspend fun getSubaddresses(): List<MoneroSubaddressInfo> {
        if (!lifecycleMutex.tryLock()) {
            subaddressesSnapshot?.let { return it }
            lifecycleMutex.lock()
        }
        return try {
            withContext(Dispatchers.IO) {
                subaddressSource.read()?.also { subaddressesSnapshot = it }.orEmpty()
            }
        } finally {
            lifecycleMutex.unlock()
        }
    }

    suspend fun createNewSubaddress(): String = withLifecycleLock(LifecycleInterruption.Preempt) {
        withContext(Dispatchers.IO) {
            // Receive pairs the returned address with the last index of the list it reads next.
            subaddressSource.create().also { refreshSubaddressesSnapshot() }
        }
    }

    // Opens and syncs must not fail over it; a dropped snapshot only makes Receive wait for the lock.
    private fun refreshSubaddressesSnapshot() {
        subaddressesSnapshot = try {
            subaddressSource.read()
        } catch (e: Exception) {
            logger.warning("subaddresses: failed to refresh the snapshot", e)
            null
        }
    }

    fun getTransactions(): List<TransactionInfo> {
        if (!isStarted) return emptyList()
        return try {
            var transactions = moneroWalletService.wallet?.history?.all ?: emptyList()
            if (transactions.isEmpty()) {
                moneroWalletService.wallet?.let {
                    moneroWalletService.wallet?.history?.refreshWithNotes(it)
                    transactions = moneroWalletService.wallet?.history?.all ?: emptyList()
                }
            }
            transactions
        } catch (e: Exception) {
            logger.warning("getTransactions: failed to fetch transactions", e)
            moneroLogger.d { "getTransactions: Failed to get transactions" }
            emptyList()
        }
    }

    override fun onRefreshed(
        wallet: Wallet?,
        full: Boolean
    ): Boolean {
        val session = activeReconciliationSession() ?: return false
        if (!isStarted) return false
        val health = try {
            walletHealthReader.snapshot(wallet)
        } catch (error: Throwable) {
            failClosedAfterPostSyncError(session, error)
            return isRefreshCallbackFullyHandled(
                coldProtocol,
                hasActiveReconciliationOperation(session),
                _spendReadiness.value,
            )
        }

        // A callback can arrive from a wallet that belonged to an earlier native session. It has
        // no authority over the active session, including its reconciliation operation.
        if (!health.callbackWalletIsCurrent) return false

        if (
            health.nativeConnectionStatus != lastLoggedConnectionStatus ||
            !health.nativeStatusError.isNullOrBlank()
        ) {
            logger.info(
                "onRefreshed: nativeConnection=${health.nativeConnectionStatus} full=$full " +
                    "current=${health.callbackWalletIsCurrent} statusOk=${health.nativeStatusIsOk} " +
                    "statusError=${health.nativeStatusError} " +
                    "serviceConnected=${health.serviceConnectionIsConnected} " +
                    "nativeConnected=${health.nativeConnectionIsConnected} " +
                    "isSynchronized=${health.walletIsSynchronized}"
            )
        }
        recordNativeConnectionError(health.nativeConnectionStatus, health.nativeStatusError)

        if (isRetryablePreservingRescanNativeStatusFailure(session, health)) {
            lastLoggedSyncProgress = -1
            setSyncStateForSession(session, AdapterState.Syncing())
            setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
            return false
        }

        if (hasMoneroNativeHealthFailure(health)) {
            val consumedAwaitingCallback = consumeFailedAwaitingReconciliationCallback(session)
            if (consumedAwaitingCallback || hasKeyImageSyncBegun(session)) {
                failClosedAfterReconciliationError(
                    session,
                    nativeHealthFailureError(health),
                )
            }
            lastLoggedSyncProgress = -1
            setSyncStateForSession(
                session,
                AdapterState.NotSynced(nativeHealthFailureError(health)),
            )
            updateHardwareReadiness(session, wallet, health)
            return !consumedAwaitingCallback && isRefreshCallbackFullyHandled(
                hardwareAccount = coldProtocol,
                hasActiveReconciliationOperation = hasActiveReconciliationOperation(session),
                spendReadiness = _spendReadiness.value,
            )
        }

        val nativeConnected =
            health.nativeConnectionIsConnected && health.serviceConnectionIsConnected
        if (nativeConnected) {
            setLastBlockInfoForSession(
                session,
                if (moneroWalletService.daemonHeight != 0L) {
                    LastBlockInfo(moneroWalletService.daemonHeight.toInt())
                } else {
                    null
                },
            )
            emitTransactionsStateUpdatedForSession(session)
        }

        val isSynchronized = health.isFullyHealthy
        val currentHeight = if (nativeConnected) {
            tryOrNull { wallet?.blockChainHeight } ?: 0
        } else {
            0
        }
        // Manager-level height is a daemon RPC and nativeConnected lies while offline, so the device
        // network state gates it too — otherwise losing network mid-sync blocks the native callback.
        val totalHeight = if (nativeConnected && !isSynchronized && connectivityManager.isConnected.value) {
            tryOrNull { WalletManager.getInstance().blockchainHeight } ?: 0L
        } else {
            0L
        }

        if (controlledLiveRefreshCommitted || !hardwareRescanPending && !controlledLiveRefreshPending) {
            setSyncStateForSession(
                session,
                resolveSyncState(nativeConnected, isSynchronized, currentHeight, totalHeight),
            )
        }
        updateHardwareReadiness(session, wallet, health)
        return isRefreshCallbackFullyHandled(
            hardwareAccount = coldProtocol,
            hasActiveReconciliationOperation = hasActiveReconciliationOperation(session),
            spendReadiness = _spendReadiness.value,
        )
    }

    private fun notConnectedState() = AdapterState.NotSynced(IllegalStateException("Not connected"))

    fun onNetworkLost() {
        publishSyncState(notConnectedState())
    }

    // `update` and not a plain assignment: the network can drop between computing a state and
    // storing it, and the CAS retry re-reads isConnected instead of overwriting NotSynced.
    private fun publishSyncState(state: AdapterState) {
        _syncState.update { if (connectivityManager.isConnected.value) state else notConnectedState() }
    }

    private fun resolveSyncState(
        nativeConnected: Boolean,
        isSynchronized: Boolean,
        currentHeight: Long,
        totalHeight: Long,
    ): AdapterState = when {
        !nativeConnected -> {
            moneroLogger.d { "MoneroKitWrapper: Not connected" }
            logger.info("onRefreshed: connection lost, setting state=NotSynced")
            lastLoggedSyncProgress = -1
            notConnectedState()
        }

        isSynchronized -> {
            moneroLogger.d { "MoneroKitWrapper: Synced" }
            logger.info("onRefreshed: wallet synchronized at height=$currentHeight")
            lastLoggedSyncProgress = 100
            AdapterState.Synced
        }

        else -> syncingState(currentHeight, totalHeight)
    }

    private fun syncingState(currentHeight: Long, totalHeight: Long): AdapterState {
        moneroLogger.d { "MoneroKitWrapper: Sync in progress" }
        if (totalHeight > 0) {
            cachedTotalHeight = totalHeight
        }
        val heightToUse = if (totalHeight > 0) totalHeight else cachedTotalHeight
        moneroLogger.d { "currentHeight = $currentHeight, totalHeight = $totalHeight" }
        val progressPercent = if (heightToUse > 0) {
            ((currentHeight.toDouble() / heightToUse) * 100).coerceIn(0.0, 100.0).toInt()
        } else {
            0
        }
        val blocksRemained = if (heightToUse > 0 && currentHeight < heightToUse) {
            heightToUse - currentHeight
        } else {
            null
        }
        logSyncProgress(progressPercent, blocksRemained)

        return AdapterState.Syncing(
            progress = progressPercent.toDouble(),
            blocksRemained = blocksRemained
        )
    }

    private fun logSyncProgress(progressPercent: Int, blocksRemained: Long?) {
        if (progressPercent == 0 && lastLoggedSyncProgress != 0) {
            logger.info("onRefreshed: sync progress started (0%)")
        }
        if (progressPercent >= 100 && lastLoggedSyncProgress < 100) {
            logger.info("onRefreshed: sync progress reached 100%")
        }
        if (progressPercent - lastLoggedSyncProgress >= 5 && progressPercent in 1..99) {
            logger.info("onRefreshed: sync progress=${progressPercent}% blocksRemained=$blocksRemained")
        }
        lastLoggedSyncProgress = progressPercent
    }

    override fun onProgress(text: String?) {
        if (!text.isNullOrBlank()) {
            logger.info("onProgress(text): $text")
        }
        moneroLogger.d { "onProgress: $text" }
    }

    override fun onProgress(n: Int) {
        if (n % 10 == 0) {
            logger.info("onProgress(value): $n")
        }
        moneroLogger.d { "onProgress: $n" }
    }

    override fun onWalletStarted(walletStatus: Wallet.Status?) {
        val session = activeReconciliationSession()
        if (!canAcceptWalletStartedCallback(session != null, isStarted, startInProgress)) return
        val activeSession = session ?: return

        val wallet = moneroWalletService.wallet
        val health = try {
            walletHealthReader.snapshot(wallet)
        } catch (error: Throwable) {
            failClosedAfterPostSyncError(activeSession, error)
            return
        }
        logger.info("onWalletStarted: $health")
        moneroLogger.d { "onWalletStarted: native health=$health" }
        val syncState = walletStartedSyncState(health)
        if (syncState is AdapterState.Synced) {
            moneroLogger.d { "MoneroKitWrapper: Synced" }
        }
        setSyncStateForSession(activeSession, syncState)
        updateHardwareReadiness(activeSession, wallet, health)
    }

    private fun updateHardwareReadiness(
        session: Long,
        wallet: Wallet?,
        health: MoneroWalletHealthSnapshot,
    ) {
        if (!coldProtocol || !isActiveReconciliationSession(session)) return
        if (explicitColdRecoveryPending) {
            setSpendReadinessForSession(session, MoneroSpendReadiness.CheckingKeyImages)
            return
        }
        if (controlledLiveRefreshPending) {
            finalizeControlledHardwareRefresh(session, wallet, health)
            return
        }
        // A reconciliation failure is terminal for this native-wallet session.  In particular,
        // do not let later generic refresh callbacks restart recovery; only an explicit key-image
        // sync (or a new wallet lifecycle) may replace this retryable failure state.
        try {
            val hasUnknownKeyImages = if (
                wallet != null && health.isFullyHealthy && _syncState.value is AdapterState.Synced
            ) {
                wallet.hasUnknownKeyImages()
            } else {
                null
            }
            if (applyHardwareReadinessSnapshot(session, health, hasUnknownKeyImages)) return

            val generation = reconciliationAwaitingCallbackGeneration(session)
            if (generation != null) {
                // Only the callback for an operation that we armed may complete the durable
                // transition. Generic/stale synchronized callbacks cannot.
                finalizeReconciliationFromCallback(session, generation, checkNotNull(wallet), health)
            } else {
                schedulePendingReconciliation(session)
            }
        } catch (error: Throwable) {
            failClosedAfterPostSyncError(session, error)
        }
    }

    private fun finalizeControlledHardwareRefresh(
        session: Long,
        wallet: Wallet?,
        health: MoneroWalletHealthSnapshot,
    ) {
        try {
            val refreshDidNotCompleteHealthy =
                !controlledLiveRefreshCommitted ||
                    !health.isFullyHealthy ||
                    wallet == null ||
                    wallet.hasUnknownKeyImages()

            if (refreshDidNotCompleteHealthy) {
                setSpendReadinessForSession(session, MoneroSpendReadiness.Syncing)
                controlledRefreshFinalization?.completeExceptionally(
                    IllegalStateException("Monero controlled refresh did not complete with a healthy wallet"),
                )
                return
            }
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                account,
                MoneroSpentReconciliationState.Ready,
            )
            controlledLiveRefreshPending = false
            controlledLiveRefreshCommitted = false
            setSpendReadinessForSession(session, MoneroSpendReadiness.Ready)
            controlledRefreshFinalization?.complete(Unit)
        } catch (error: Throwable) {
            setSpendReadinessForSession(session, MoneroSpendReadiness.Syncing)
            controlledRefreshFinalization?.completeExceptionally(
                error,
            )
        }
    }

    /** Production callback readiness routing, kept JNI-free for deterministic manager tests. */
    internal fun applyHardwareReadinessSnapshot(
        session: Long,
        health: MoneroWalletHealthSnapshot,
        hasUnknownKeyImages: Boolean?,
    ): Boolean {
        if (!coldProtocol || !isActiveReconciliationSession(session)) return true
        if (_spendReadiness.value == MoneroSpendReadiness.ReconciliationFailed) return true
        if (_syncState.value !is AdapterState.Synced || !health.isFullyHealthy ||
            hasUnknownKeyImages == null
        ) {
            val awaitingExpectedRescan =
                reconciliationAwaitingCallbackGeneration(session) != null &&
                    !hasMoneroNativeHealthFailure(health)
            if (hasKeyImageSyncBegun(session) && !awaitingExpectedRescan) {
                failClosedAfterReconciliationError(
                    session,
                    IllegalStateException("Monero spent-status readiness is no longer healthy"),
                )
            } else {
                setSpendReadinessForSession(
                    session,
                    if (hasActiveReconciliationOperation(session)) {
                        MoneroSpendReadiness.ReconcilingSpentStatus
                    } else {
                        MoneroSpendReadiness.Syncing
                    },
                )
            }
            return true
        }
        if (hasUnknownKeyImages) {
            clearReconciliationOperation(session)
            setSpendReadinessForSession(session, MoneroSpendReadiness.NeedsKeyImageSync)
            return true
        }

        val durableState = restoreSettingsManager.moneroSpentReconciliationState(account)
        if (durableState == MoneroSpentReconciliationState.Ready) {
            setSpendReadinessForSession(
                session,
                if (canExposeMoneroSpendReady(true, health, false, durableState)) {
                    MoneroSpendReadiness.Ready
                } else {
                    MoneroSpendReadiness.ReconcilingSpentStatus
                },
            )
            return true
        }
        return false
    }

    private fun finalizeTrustedKeyImageSync(session: Long, wallet: Wallet) {
        try {
            when (
                trustedKeyImageSyncFinalizationOutcome(
                    health = walletHealthReader.snapshot(wallet),
                    hasUnknownKeyImages = wallet.hasUnknownKeyImages(),
                    syncState = _syncState.value,
                )
            ) {
                TrustedKeyImageSyncFinalizationOutcome.NeedsKeyImageSync -> {
                    clearReconciliationOperation(session)
                    setSpendReadinessForSession(session, MoneroSpendReadiness.NeedsKeyImageSync)
                }
                TrustedKeyImageSyncFinalizationOutcome.Ready -> {
                    restoreSettingsManager.saveMoneroSpentReconciliationState(
                        account,
                        MoneroSpentReconciliationState.Ready,
                    )
                    clearReconciliationOperation(session)
                    setSpendReadinessForSession(session, MoneroSpendReadiness.Ready)
                }
                TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed ->
                    failClosedAfterReconciliationError(
                        session,
                        IllegalStateException("Trusted Monero key-image sync finalization is not ready"),
                    )
            }
        } catch (error: Throwable) {
            failClosedAfterReconciliationError(session, error)
            throw error
        }
    }

    /**
     * Starts the non-destructive daemon spent-status query after the Trezor operation has been
     * released. The second pause/drain prevents a pre-existing generic refresh callback from
     * being mistaken for this request's completion.
     */
    private suspend fun requestSpentReconciliation(session: Long, wallet: Wallet) {
        if (!isActiveReconciliationSession(session)) return
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
        setSyncStateForSession(session, AdapterState.Syncing())
        try {
            applySpentStatusRequestResult(session, spentStatusRecovery.request(session, wallet))
        } catch (error: Throwable) {
            failClosedAfterReconciliationError(session, error)
            throw error
        }
    }

    /** JNI-free terminal wiring for spent-status request results. */
    internal fun applySpentStatusRequestResult(
        session: Long,
        result: MoneroSpentStatusRequestResult,
    ) {
        when (result) {
            MoneroSpentStatusRequestResult.Retry -> failClosedAfterReconciliationError(
                session,
                IllegalStateException("Monero wallet is unhealthy before spent-status reconciliation"),
            )
            MoneroSpentStatusRequestResult.NeedsKeyImageSync -> {
                clearReconciliationOperation(session)
                setSpendReadinessForSession(session, MoneroSpendReadiness.NeedsKeyImageSync)
            }
            MoneroSpentStatusRequestResult.AwaitingCallback -> Unit
        }
    }

    /** Recovers legacy/absent and PENDING records after ordinary synchronization, no Trezor needed. */
    private fun schedulePendingReconciliation(session: Long) {
        val generation = spentStatusReconciler.beginRecovery(session) ?: return
        val expectedPhase = MoneroReconciliationPhase.RecoveryRequested(generation)
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
        launchReconciliation(session, expectedPhase) {
            val wallet = moneroWalletService.wallet
            try {
                if (wallet == null) {
                    clearReconciliationOperation(session)
                    setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
                } else {
                    requestSpentReconciliation(session, wallet)
                }
            } catch (error: Throwable) {
                failClosedAfterReconciliationError(session, error)
            }
        }
    }

    private fun finalizeReconciliationFromCallback(
        session: Long,
        generation: Long,
        wallet: Wallet,
        health: MoneroWalletHealthSnapshot,
    ) {
        val callbackIsSuccessful = canFinalizeSpentReconciliation(health)
        val callbackDisposition = spentStatusRecovery.acceptCallback(
            session = session,
            generation = generation,
            callbackIsSuccessful = callbackIsSuccessful,
        )
        if (callbackDisposition == ReconciliationCallbackDisposition.Ignore) return
        if (callbackDisposition == ReconciliationCallbackDisposition.FailClosed) {
            setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
            return
        }
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconcilingSpentStatus)
        launchReconciliation(
            session = session,
            expectedPhase = MoneroReconciliationPhase.Finalizing(generation),
            expectedWallet = wallet,
        ) {
            try {
                spentStatusRecovery.finalizeAcceptedCallback(session, wallet)
                setSpendReadinessForSession(session, MoneroSpendReadiness.Ready)
            } catch (error: Throwable) {
                failClosedAfterReconciliationError(session, error)
            }
        }
    }

    private fun failClosedAfterReconciliationError(session: Long, error: Throwable) {
        clearReconciliationOperation(session)
        // Do not inspect JNI state while failing closed: the operation may have failed because
        // the callback/session was stale or native health had already become invalid.
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconciliationFailed)
        moneroLogger.e(error) { "Monero spent-status reconciliation failed" }
    }

    private fun failClosedAfterPostSyncError(session: Long, error: Throwable) {
        failClosedAfterReconciliationError(session, error)
    }

    private fun hasKeyImageSyncBegun(session: Long): Boolean = keyImageSyncSession == session

    private fun reconciliationAwaitingCallbackGeneration(session: Long): Long? =
        spentStatusReconciler.awaitingCallbackGeneration(session)

    private fun hasActiveReconciliationOperation(session: Long): Boolean =
        spentStatusReconciler.hasActiveOperation(session)

    private fun clearReconciliationOperation(session: Long) {
        spentStatusReconciler.clearOperation(session)
    }

    private fun consumeFailedAwaitingReconciliationCallback(session: Long): Boolean =
        spentStatusReconciler.consumeFailedAwaitingCallback(session)

    private fun isRetryablePreservingRescanNativeStatusFailure(
        session: Long,
        health: MoneroWalletHealthSnapshot,
    ): Boolean =
        reconciliationAwaitingCallbackGeneration(session) != null &&
            hasConnectedNativeStatusFailure(health)

    private fun hasConnectedNativeStatusFailure(health: MoneroWalletHealthSnapshot): Boolean =
        !health.nativeStatusIsOk &&
            health.nativeConnectionIsConnected &&
            health.serviceConnectionIsConnected

    private fun activeReconciliationSession(): Long? = spentStatusReconciler.activeSession()

    private fun requireActiveReconciliationSession(): Long =
        checkNotNull(spentStatusReconciler.activeSession()) {
            "Monero reconciliation session is not active"
        }

    private fun isActiveReconciliationSession(session: Long): Boolean =
        spentStatusReconciler.isActive(session)

    private fun setSpendReadinessForSession(
        session: Long,
        readiness: MoneroSpendReadiness,
    ) {
        if (!spentStatusReconciler.isActive(session)) return
        _spendReadiness.value = readiness
        if (readiness.isTerminalKeyImageSyncOutcome) {
            clearKeyImageSyncSession(session)
        }
        if (!spentStatusReconciler.isActive(session)) {
            _spendReadiness.compareAndSet(readiness, MoneroSpendReadiness.Syncing)
        }
    }

    private fun clearKeyImageSyncSession(session: Long) {
        if (keyImageSyncSession == session) {
            keyImageSyncSession = null
        }
    }

    private fun setSyncStateForSession(session: Long, state: AdapterState) {
        _syncState.update { currentState ->
            if (!spentStatusReconciler.isActive(session)) {
                currentState
            } else if (connectivityManager.isConnected.value) {
                state
            } else {
                notConnectedState()
            }
        }
    }

    private fun setLastBlockInfoForSession(session: Long, lastBlockInfo: LastBlockInfo?) {
        if (spentStatusReconciler.isActive(session)) {
            _lastBlockInfoFlow.value = lastBlockInfo
        }
    }

    private fun emitTransactionsStateUpdatedForSession(session: Long) {
        if (spentStatusReconciler.isActive(session)) {
            _transactionsStateUpdatedFlow.tryEmit(Unit)
        }
    }

    private fun activateReconciliationSession() {
        spentStatusReconciler.activate()
        keyImageSyncSession = null
        if (hardwareWallet) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    private fun deactivateReconciliationSession() {
        spentStatusReconciler.deactivate()
        keyImageSyncSession = null
        explicitColdRecoveryPending = false
        controlledLiveRefreshPending = false
        controlledLiveRefreshCommitted = false
        if (hardwareWallet) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    private fun launchReconciliation(
        session: Long,
        expectedPhase: MoneroReconciliationPhase,
        expectedWallet: Wallet? = null,
        block: suspend () -> Unit,
    ) {
        spentStatusReconciler.launch(session, expectedPhase) {
            lifecycleMutex.withLock {
                val valid = spentStatusReconciler.isInPhase(session, expectedPhase) &&
                    isStarted &&
                    (expectedWallet == null || moneroWalletService.wallet === expectedWallet)
                if (!valid) return@withLock
                block()
            }
        }
    }

    override fun onWalletOpen(device: Wallet.Device?) {
        moneroLogger.d { "onWalletOpen: $device" }
    }
}

/** How the wallet reaches its device: Trezor signs cold, a Ledger answers wallet2 as it syncs. */
internal enum class MoneroDeviceProtocol { None, Cold, Proxy }

/** Why a Ledger refresh ends at its next batch boundary; a later value outranks an earlier one. */
private enum class ProxySyncEarlyEnd { Checkpoint, UserYield, Interruption }

/** What a Ledger sync pass may do; coalesced triggers keep the one that may do most. */
private enum class ProxySyncPass { SyncStarted, Attach, Refresh }

private enum class LifecycleInterruption { Stop, Preempt }

/** A device code wins; a status without one failed on the node when it reports no connection. */
private fun Wallet.Status?.hardwareFailure() = HardwareWalletOperationException(
    this?.hardwareWalletError ?: if (this?.connectionStatus == ConnectionStatus.ConnectionStatus_Disconnected) {
        HardwareWalletErrorCode.Network
    } else {
        HardwareWalletErrorCode.Protocol
    },
    this?.errorString,
)

internal val Account.moneroDeviceProtocol: MoneroDeviceProtocol
    get() = when (type) {
        is AccountType.TrezorDevice -> MoneroDeviceProtocol.Cold
        is AccountType.LedgerDevice -> MoneroDeviceProtocol.Proxy
        else -> MoneroDeviceProtocol.None
    }

/** Pure gates retained here so the fail-closed lifecycle contract is locally JVM-testable. */
internal fun canExposeMoneroSpendReady(
    hardwareWallet: Boolean,
    health: MoneroWalletHealthSnapshot,
    hasUnknownKeyImages: Boolean,
    durableState: MoneroSpentReconciliationState,
): Boolean =
    !hardwareWallet || (
        health.isFullyHealthy &&
            !hasUnknownKeyImages &&
            durableState == MoneroSpentReconciliationState.Ready
        )

/** JVM-only portion of the hardware-send boundary, checked before accessing the native wallet. */
internal fun isHardwareSpendLifecycleReady(
    syncState: AdapterState,
    durableState: MoneroSpentReconciliationState,
    spendReadiness: MoneroSpendReadiness,
): Boolean =
    syncState is AdapterState.Synced &&
        durableState == MoneroSpentReconciliationState.Ready &&
        spendReadiness == MoneroSpendReadiness.Ready

/**
 * Native start may synchronously deliver onWalletStarted() before [isStarted] is committed.
 * A callback is therefore accepted only for the active session while that start is in progress,
 * or once the start has completed.  With neither state true, stopped-session callbacks fail
 * closed.
 */
internal fun canAcceptWalletStartedCallback(
    hasActiveCurrentSession: Boolean,
    isStarted: Boolean,
    startInProgress: Boolean,
): Boolean = hasActiveCurrentSession && (isStarted || startInProgress)

internal fun walletStartedSyncState(health: MoneroWalletHealthSnapshot): AdapterState =
    when {
        hasMoneroNativeHealthFailure(health) ->
            AdapterState.NotSynced(IllegalStateException("Monero wallet native health check failed"))
        health.isFullyHealthy -> AdapterState.Synced
        else -> AdapterState.Syncing()
    }

internal fun hasMoneroNativeHealthFailure(health: MoneroWalletHealthSnapshot): Boolean =
    !health.callbackWalletIsCurrent ||
        !health.nativeStatusIsOk ||
        !health.nativeConnectionIsConnected

internal fun nativeHealthFailureError(health: MoneroWalletHealthSnapshot): IllegalStateException =
    IllegalStateException(
        health.nativeStatusError?.takeIf { it.isNotBlank() }
            ?: "Monero wallet native health check failed",
    )

internal fun canFinalizeSpentReconciliation(health: MoneroWalletHealthSnapshot): Boolean =
    health.isFullyHealthy

internal enum class TrustedKeyImageSyncFinalizationOutcome {
    Ready,
    NeedsKeyImageSync,
    ReconciliationFailed,
}

internal fun trustedKeyImageSyncFinalizationOutcome(
    health: MoneroWalletHealthSnapshot,
    hasUnknownKeyImages: Boolean,
    syncState: AdapterState,
): TrustedKeyImageSyncFinalizationOutcome =
    when {
        hasUnknownKeyImages -> TrustedKeyImageSyncFinalizationOutcome.NeedsKeyImageSync
        health.isFullyHealthy && syncState is AdapterState.Synced ->
            TrustedKeyImageSyncFinalizationOutcome.Ready
        else -> TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed
    }

/**
 * A true return asks MoneroWalletService to suppress a same-tip callback. Hardware
 * reconciliation must therefore keep returning false until it reaches a terminal state: either
 * durably ready, or blocked on the user supplying key images.
 */
internal fun isRefreshCallbackFullyHandled(
    hardwareAccount: Boolean,
    hasActiveReconciliationOperation: Boolean,
    spendReadiness: MoneroSpendReadiness,
): Boolean =
    !hardwareAccount || (
        !hasActiveReconciliationOperation &&
            (spendReadiness == MoneroSpendReadiness.Ready ||
                spendReadiness == MoneroSpendReadiness.NeedsKeyImageSync ||
                spendReadiness == MoneroSpendReadiness.ReconciliationFailed)
        )

private val MoneroSpendReadiness.isTerminalKeyImageSyncOutcome: Boolean
    get() = this == MoneroSpendReadiness.Ready ||
        this == MoneroSpendReadiness.NeedsKeyImageSync ||
        this == MoneroSpendReadiness.ReconciliationFailed

internal enum class ColdKeyImageSyncNextStep {
    TrustedReady,
    PreserveKeyImagesRescan,
    NeedsKeyImageSync,
}

internal fun coldKeyImageSyncNextStep(
    spentStatusVerified: Boolean,
    hasUnknownKeyImages: Boolean,
): ColdKeyImageSyncNextStep =
    if (hasUnknownKeyImages) ColdKeyImageSyncNextStep.NeedsKeyImageSync
    else if (spentStatusVerified) ColdKeyImageSyncNextStep.TrustedReady
    else ColdKeyImageSyncNextStep.PreserveKeyImagesRescan
