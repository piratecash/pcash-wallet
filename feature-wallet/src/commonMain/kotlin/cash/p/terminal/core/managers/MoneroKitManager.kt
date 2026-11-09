package cash.p.terminal.core.managers

import cash.p.terminal.core.onPollingStartedSuspend
import cash.p.terminal.core.onPollingStoppedSuspend
import cash.p.terminal.core.MoneroRescanException
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.MoneroAdapter
import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.usecase.MoneroWalletServiceProvider
import cash.p.terminal.core.usecase.MoneroWalletStorage
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.core.utils.MoneroConfig
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.SeedAccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAccountManager
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import co.touchlab.kermit.Logger
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val moneroLogger = Logger.withTag("MoneroKitManager")

class MoneroKitManager(
    private val moneroWalletServiceProvider: MoneroWalletServiceProvider,
    private val backgroundManager: IBackgroundManager,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val connectivityManager: IConnectivityManager,
    private val dispatcherProvider: DispatcherProvider,
    private val storageRouter: StorageRouter,
    private val removeMoneroWalletFilesUseCase: IRemoveMoneroWalletFilesUseCase,
    private val networkErrorTracker: NetworkErrorTracker,
    private val offlineModeManager: OfflineModeManager,
) {
    // Serializes account-lifecycle mutations (activate / unlink / stop) so the process-global
    // MoneroHttpClient observer factory is set/cleared without racing a concurrent teardown.
    private val accountMutex = Mutex()
    private val pollingSessionCount = AtomicInteger(0)
    private val coroutineScope =
        CoroutineScope(dispatcherProvider.io + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            moneroLogger.e(throwable) { "Coroutine error" }
        })
    var moneroKitWrapper: MoneroKitWrapper? = null
    private val lifecycleJobs = mutableListOf<Job>()

    private var useCount = AtomicInteger(0)
    var currentAccount: Account? = null
        private set
    private val moneroKitStoppedSubject = PublishSubject.create<Unit>()
    private val moneroTrezorGateway: MoneroHardwareGateway by inject(MoneroHardwareGateway::class.java)
    private val trezorAttachments: TrezorAttachments by inject(TrezorAttachments::class.java)
    private val accountManager: IAccountManager by inject(IAccountManager::class.java)

    val kitStoppedObservable: Observable<Unit>
        get() = moneroKitStoppedSubject

    init {
        // Cached device sessions outlive any wallet, so their wipe triggers run for the whole process.
        coroutineScope.launch {
            trezorAttachments.detached.collect {
                moneroTrezorGateway.retainSessions(emptySet())
                moneroKitWrapper?.onTrezorDetached()
            }
        }
        coroutineScope.launch {
            accountManager.accountsDeletedFlowable.asFlow().collect {
                moneroTrezorGateway.retainSessions(accountManager.accounts.mapTo(HashSet(), Account::id))
            }
        }
    }

    internal suspend fun <T> withExclusiveNativeWallet(
        block: suspend () -> T,
    ): T = accountMutex.withLock {
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

    suspend fun getMoneroKitWrapper(account: Account): MoneroKitWrapper = accountMutex.withLock {
        if (this.moneroKitWrapper != null && currentAccount != account) {
            stopKit() // also nulls moneroKitWrapper and clears the factory
        }

        if (this.moneroKitWrapper == null) {
            val accountType = account.type
            this.moneroKitWrapper = when {
                accountType is AccountType.MnemonicMonero ||
                        accountType is SeedAccountType ||
                        accountType is AccountType.TrezorDevice
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
                        retryOnLifecycleEvent =
                            accountType is AccountType.TrezorDevice &&
                                partial?.isRestartableAfterFailedStart == true &&
                                e.isRetryableTrezorStartupFailure()
                        // Adapters reach the published wrapper without accountMutex, so a pass may already be queued on it.
                        if (!retryOnLifecycleEvent) partial?.release()
                        cleanupSucceeded = true
                    } catch (stopError: Throwable) {
                        e.addSuppressed(stopError)
                    }
                }
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
            moneroWalletService = moneroWalletServiceProvider.service,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = networkErrorTracker,
            moneroTrezorGateway = moneroTrezorGateway,
            connectivityManager = connectivityManager,
            backgroundManager = backgroundManager,
        )
    }

    suspend fun unlink(account: Account) = accountMutex.withLock {
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
    suspend fun rescan(account: Account, newHeight: Long) = accountMutex.withLock {
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
            storageRouter.account(account.id)?.moneroFileDao()?.deleteAssociatedRecord(account.id)
            restoreSettingsManager.saveMoneroRestoreHeight(account, newHeight)
        }
    }

    suspend fun rescanIfActive(account: Account, newHeight: Long): Boolean = accountMutex.withLock {
        if (currentAccount?.id != account.id) return@withLock false
        moneroKitWrapper?.rescan(newHeight)
        true
    }

    suspend fun pauseNetwork(account: Account) = accountMutex.withLock {
        if (account == currentAccount) moneroKitWrapper?.pause()
    }

    suspend fun resumeNetwork(account: Account) = accountMutex.withLock {
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

    private suspend fun stopKit() {
        lifecycleJobs.forEach { it.cancel() }
        lifecycleJobs.clear()
        val wrapper = moneroKitWrapper
        try {
            withContext(NonCancellable) { wrapper?.release() }
        } catch (error: Throwable) {
            currentAccount?.let(::subscribeToEvents)
            throw error
        }
        currentAccount = null
        moneroKitWrapper = null
        MoneroHttpClient.eventListenerFactory = null
    }

    private suspend fun startKit() {
        val wrapper = moneroKitWrapper ?: return
        wrapper.start(localOnly = isNetworkPaused(wrapper.account))
    }

    private suspend fun stopAndSaveKit() {
        moneroKitWrapper?.stop(saveWallet = true)
    }

    private suspend fun resumeOrStartKit() {
        val wrapper = moneroKitWrapper ?: return
        if (isNetworkPaused(wrapper.account)) {
            wrapper.start(localOnly = true)
            return
        }
        if (!wrapper.resume()) {
            startKit()
        }
    }

    /**
     * [deviceCue]: opening the app or attaching the Trezor is the user's cue, so an unconfirmed session is asked once.
     * Judges only its own error: a concurrent trigger may already be starting the wallet again.
     */
    private suspend fun resumeOrStartKitOnLifecycleEvent(deviceCue: Long?): Boolean = try {
        resumeOrStartKit()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (deviceCue != null && error.isPendingDeviceVerification()) {
            moneroKitWrapper?.askDevice(deviceCue)
        } else if (!error.isRetryableTrezorStartupFailure()) {
            moneroLogger.e(error) { "Failed to resume Monero wallet on a lifecycle event" }
        }
        false
    }

    private fun isNetworkPaused(account: Account): Boolean =
        offlineModeManager.isNetworkPaused(account, BlockchainType.Monero)

    private fun subscribeToEvents(account: Account) {
        lifecycleJobs.forEach { it.cancel() }
        lifecycleJobs.clear()

        lifecycleJobs += coroutineScope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    resumeOrStartKitOnLifecycleEvent(deviceCue = moneroKitWrapper?.attendedPassesFinished)
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
                        if (!resumeOrStartKitOnLifecycleEvent(deviceCue = null)) return@collect
                        // resumeOrStartKit is a no-op for an already running kit, so without an
                        // explicit refresh the state would stay NotSynced until the next callback.
                        moneroKitWrapper?.syncUnattended()
                    }
                }
        }
        if (account.type is AccountType.TrezorDevice) subscribeToTrezor()
        lifecycleJobs += coroutineScope.launch {
            moneroKitWrapper?.let { w ->
                w.syncState.map { it is AdapterState.Synced }.distinctUntilChanged()
                    .collect { synced ->
                        if (synced) tryOrNull { w.saveSynced() }
                    }
            }
        }
    }

    private fun subscribeToTrezor() {
        lifecycleJobs += coroutineScope.launch {
            trezorAttachments.attached.collect { syncTrezorAfterAttach() }
        }
        // The native refresh thread never runs for a Trezor, so new blocks arrive only through this loop.
        lifecycleJobs += coroutineScope.launch {
            while (true) {
                delay(TREZOR_SYNC_INTERVAL)
                moneroKitWrapper?.syncUnattended()
            }
        }
    }

    // A wallet closed after a device failure reopens here; the collector must outlive a failed sync.
    private suspend fun syncTrezorAfterAttach() {
        val cue = moneroKitWrapper?.attendedPassesFinished ?: return
        try {
            if (resumeOrStartKitOnLifecycleEvent(cue)) moneroKitWrapper?.syncAskingDevice(cue)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            moneroLogger.w(error) { "Failed to sync Monero wallet after Trezor attach" }
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

// A Monero block takes about two minutes; this keeps a connected Trezor well within one block.
private val TREZOR_SYNC_INTERVAL = 30.seconds

// Four 30 s passes outlast one ~2 min Monero block, so a node merely a block ahead never shows as an error.
private const val NODE_BEHIND_PASSES_BEFORE_SHOWN = 4

private val TREZOR_CHECKPOINT_INTERVAL = 2.minutes

/** wallet_api reports success without scanning while its node lags behind, so only heights prove a sync. */
internal fun moneroScanReachedDaemon(walletHeight: Long, daemonHeight: Long, daemonTargetHeight: Long): Boolean =
    daemonHeight > 1 && daemonHeight >= daemonTargetHeight && walletHeight >= daemonHeight

private fun Throwable.isRetryableTrezorStartupFailure(): Boolean =
    this is HardwareWalletOperationException && when (error) {
        HardwareWalletErrorCode.DeviceNotFound,
        HardwareWalletErrorCode.AcquireTimeout,
        HardwareWalletErrorCode.PermissionDenied,
        HardwareWalletErrorCode.PendingDeviceVerification -> true
        else -> false
    }

private fun Throwable.isPendingDeviceVerification(): Boolean =
    (this as? HardwareWalletOperationException)?.error == HardwareWalletErrorCode.PendingDeviceVerification

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

    /** A Trezor wallet's service stays paused, so its service connection state never updates. */
    val isNativelyHealthy: Boolean
        get() = callbackWalletIsCurrent && nativeStatusIsOk && nativeConnectionIsConnected && walletIsSynchronized
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

internal fun interface ControlledHardwareRefreshAborter {
    fun abort(error: Throwable)
}

internal interface ControlledHardwareRefreshOperation {
    /** An attended request never joins an unattended one: it waits for it to end and runs its own. */
    val attended: Boolean get() = false
    /** A reset request never joins a pass that does not reset. */
    val resets: Boolean get() = false
    suspend fun run(aborter: ControlledHardwareRefreshAborter)
    fun abort(error: Throwable)
}

internal class ControlledHardwareRefreshCoordinator(
    private val scope: CoroutineScope,
) {
    private class Generation(
        val operation: Deferred<Unit>,
        val attended: Boolean,
        val resets: Boolean,
        var waiterCount: Int,
    ) {
        fun canBeJoinedBy(refreshOperation: ControlledHardwareRefreshOperation): Boolean =
            !operation.isCancelled &&
                (attended || !refreshOperation.attended) &&
                (resets || !refreshOperation.resets)
    }

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
            var created = false
            val acquiredGeneration = mutex.withLock {
                val current = generation
                when {
                    current == null || current.operation.isCompleted -> {
                        created = true
                        Generation(
                            operation = scope.async {
                                val aborter =
                                    OneShotControlledHardwareRefreshAborter(refreshOperation::abort)
                                // The operation owns its lifecycle boundary and must abort before
                                // releasing it. A coordinator-level fallback would run outside that
                                // boundary and could race a queued wallet stop/restart.
                                refreshOperation.run(aborter)
                            },
                            attended = refreshOperation.attended,
                            resets = refreshOperation.resets,
                            waiterCount = 1,
                        ).also { generation = it }
                    }

                    !current.canBeJoinedBy(refreshOperation) -> current
                    else -> {
                        current.waiterCount += 1
                        current
                    }
                }
            }
            // A creator keeps its generation even when it has already failed, or it would rerun it.
            if (created || acquiredGeneration.canBeJoinedBy(refreshOperation)) return acquiredGeneration
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

class MoneroKitWrapper(
    private val moneroWalletService: MoneroWalletService,
    private val restoreSettingsManager: RestoreSettingsManager,
    val account: Account,
    private val dispatcherProvider: DispatcherProvider,
    private val networkErrorTracker: NetworkErrorTracker,
    private val moneroTrezorGateway: MoneroHardwareGateway,
    private val connectivityManager: IConnectivityManager,
    private val backgroundManager: IBackgroundManager,
    private val walletHealthReader: MoneroWalletHealthReader =
        ServiceMoneroWalletHealthReader(moneroWalletService),
    private val daemonSelector: MoneroDaemonSelector = AutoMoneroDaemonSelector,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : MoneroWalletService.Observer {
    private val storageRouter: StorageRouter by inject(StorageRouter::class.java)
    private val moneroFileDao get() = storageRouter.account(account.id)?.moneroFileDao()
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
    private val logger = AppLogger("monero-kit").getScoped(account.id).forAccount(account.id)

    @Volatile
    private var lastLoggedConnectionStatus: ConnectionStatus? = null
    private var lastLoggedSyncProgress: Int = -1

    @Volatile
    private var isStarted = false
    @Volatile
    private var nativeCloseSequence = 0
    // Written under lifecycleMutex: the manager has dropped this wrapper.
    private var released = false
    private var isPaused = false
    // The owner's offline-mode decision; unlike isPaused it outlives a closed wallet. Written under lifecycleMutex.
    @Volatile
    private var networkPaused = false
    // Opened from disk because no node answered; refresh/resume may only attach a node, never close it.
    private var openedWithoutNode = false
    @Volatile
    private var startInProgress = false

    internal val isRestartableAfterFailedStart: Boolean
        get() = !isStarted && !startInProgress
    private val lifecycleMutex = Mutex()
    private val passScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val controlledRefreshCoordinator = ControlledHardwareRefreshCoordinator(passScope)
    private val spentStatusReconciler = MoneroSpentStatusReconciler()
    // The chain height a Trezor pass last stored in this native session.
    private var storedChainHeight: Long? = null
    // Latched on the refresh thread when a Trezor pass is asked to end at its next batch boundary.
    @Volatile
    private var trezorPassEarlyEnd: TrezorPassEarlyEnd? = null
    @Volatile
    private var trezorCheckpointAt: TimeMark? = null
    // A pass scanning on the view key would otherwise notice the missing device only at its checkpoint.
    @Volatile
    private var trezorDetached = false
    // Consecutive Trezor syncs that ended behind the node; touched only under lifecycleMutex.
    private var nodeBehindStreak = 0
    // Interruptions waiting for lifecycleMutex; the holder's pass ends at its next batch boundary for them.
    private val interruptionsWaiting = AtomicInteger()
    // Attended syncs waiting for an unattended one to give the device up.
    private val attendedSyncsWaiting = AtomicInteger()
    /** A user cue reads this first; an attended pass finished since, rejected or not, already answered that cue. */
    @Volatile
    internal var attendedPassesFinished = 0L
        private set
    private val hardwareAccount = account.type is AccountType.TrezorDevice
    val hardwareWallet: Boolean
        get() = hardwareAccount

    private val _spendReadiness = MutableStateFlow(
        if (hardwareAccount) MoneroSpendReadiness.Syncing else MoneroSpendReadiness.Ready,
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


    private suspend fun restoreFromSeedAccount(
        account: Account,
        height: Long
    ) {
        logger.info("restoreFromSeedAccount: height=$height")
        val accountType = account.type as? SeedAccountType
            ?: throw UnsupportedAccountException()
        val restoredAccount = checkNotNull(moneroWalletUseCase.restoreFromSeedAccount(accountType, height)) {
            "Failed to restore Monero wallet from seed"
        }
        moneroFileDao?.insert(
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
    ) = lifecycleMutex.withLock {
        networkPaused = localOnly
        startInternal(fixIfCorruptedFile, localOnly)
    }

    private suspend fun startInternal(
        fixIfCorruptedFile: Boolean = true,
        localOnly: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            if (staysClosed(localOnly)) return@withContext
            if (!isStarted) {
                startInProgress = true
                logger.info("start: requested, fixIfCorruptedFile=$fixIfCorruptedFile, isStarted=$isStarted")
                lastLoggedSyncProgress = -1
                lastLoggedConnectionStatus = null
                storedForSync = false
                // A previously synchronized hardware wallet must never retain its spend
                // authorization while a new native session is being opened.
                if (hardwareAccount) {
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
                        startLocalOnly(credentials)
                        return@withContext
                    }

                    val selectedNode = MoneroConfig.autoSelectNode()
                    if (selectedNode != null) {
                        logger.info("start: auto-selected node=$selectedNode")
                        WalletManager.getInstance()
                            .setDaemon(selectedNode)
                    } else {
                        logger.info("start: autoSelectNode returned null, set first default node")
                        WalletManager.getInstance()
                            .setDaemon(NodeInfo.fromString(DefaultNodes.entries.first().uri))
                    }

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
                    // A Trezor wallet is started by its own open, and a pass that failed after it closed it again.
                    if (!hardwareAccount) isStarted = true
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

    // Opening a Trezor wallet is a device pass, and offline there is none: it stays closed on its last saved state.
    private fun staysClosed(localOnly: Boolean): Boolean =
        hardwareAccount && (localOnly || !admitsTrezorPass(attended = false))

    private fun startLocalOnly(credentials: MoneroWalletCredentials) {
        moneroWalletService.setObserver(this)
        when (val result = moneroWalletService.startOffline(credentials.fileName, credentials.password)) {
            LocalOpenResult.Opened -> {
                isStarted = true
                isPaused = true
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
            is SeedAccountType -> mnemonicCredentials()
            is AccountType.TrezorDevice -> trezorCredentials()
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
        logger.info("start: using seed account")
        if (moneroFileDao?.getAssociatedRecord(account.id) == null) {
            logger.info("start: no associated wallet files, restoring from mnemonic")
            val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
            val height = restoreSettings.birthdayHeight
                ?: validateMoneroHeightUseCase.getTodayHeight()
            check(height != -1L) { "Monero restore height can't be -1" }
            restoreFromSeedAccount(account = account, height = height)
        }
        val record = requireNotNull(moneroFileDao?.getAssociatedRecord(accountId = account.id)) {
            "Account does not have a valid Monero file association"
        }
        return MoneroWalletCredentials(record.fileName.value, record.password.value)
    }

    private suspend fun trezorCredentials(): MoneroWalletCredentials {
        val record = requireNotNull(moneroFileDao?.getAssociatedRecord(account.id)) {
            "Trezor account has no Monero wallet files"
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
        if (hardwareAccount) {
            startTrezorWallet(MoneroWalletCredentials(walletFileName, walletPassword))
        } else {
            startService(walletFileName, walletPassword, fixIfCorruptedFile)
        }
    }

    private fun handleStartFailure(error: Exception) {
        deactivateReconciliationSession()
        if (hardwareAccount) {
            val walletClosed = closeHardwareWalletAfterFailedStart(error)
            // The failed start cannot authorize reconciliation or spending, but a native wallet
            // that cleanup could not close must remain owned so a later stop() retries it.
            isStarted = !walletClosed
            isPaused = !walletClosed
            // A failed start is never a valid session, even if the native cleanup also failed.
            publishTrezorSyncFailure(error)
        } else {
            _syncState.value = AdapterState.NotSynced(error)
        }
        logger.warning("start: failed with exception", error)
        moneroLogger.e(error) { "Failed to start Monero wallet" }
        if (hardwareAccount) throw error
    }

    private fun closeHardwareWalletAfterFailedStart(operationError: Throwable): Boolean {
        val wallet = moneroWalletService.wallet ?: return true
        return try {
            val closed = closeNativeWallet(saveWallet = false)
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

    private fun startHardwareServicePaused(
        walletFileName: String,
        walletPassword: String,
    ) {
        val status = moneroWalletService.startPaused(walletFileName, walletPassword)
        recordNativeConnectionError(status?.connectionStatus, status?.errorString)
        if (status?.isOk == true) return
        throw HardwareWalletOperationException(
            status?.hardwareWalletError ?: HardwareWalletErrorCode.Protocol,
            status?.errorString,
        )
    }

    /** Opening is part of the device pass: the wallet never opens without it, unattended at start. */
    private suspend fun startTrezorWallet(credentials: MoneroWalletCredentials) {
        var opened = false
        try {
            syncTrezorWalletSession(
                attended = false,
                forcedMode = null,
                aborter = ::abortControlledHardwareWallet,
            ) {
                openTrezorWallet(credentials)
                opened = true
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A pass that failed after the open already closed the wallet; the next trigger reopens it.
            if (!opened) throw error
        }
    }

    private fun openTrezorWallet(credentials: MoneroWalletCredentials) {
        openPausedHardwareWalletForRefresh {
            startHardwareServicePaused(credentials.fileName, credentials.password)
        }
    }

    /** Runs while the lifecycle mutex and the Trezor session are both owned; a checkpoint goes on in the same session. */
    internal fun syncTrezorWalletLeaseOwned(
        attended: Boolean,
        forcedMode: HardwareKeyImageRefreshResult.Mode?,
    ): HardwareKeyImageRefreshResult {
        var result = refreshHardwareKeyImagesLeaseOwned(attended, forcedMode)
        // Anyone who started waiting after the last block still gets the device before the next pass.
        while (!result.completed && requestedTrezorEarlyEnd(attended) == TrezorPassEarlyEnd.Checkpoint) {
            result = refreshHardwareKeyImagesLeaseOwned(attended, forcedMode = null)
        }
        return result
    }

    /** One native pass; stores what it changed before the device is released. */
    internal open fun refreshHardwareKeyImagesLeaseOwned(
        attended: Boolean,
        forcedMode: HardwareKeyImageRefreshResult.Mode?,
    ): HardwareKeyImageRefreshResult {
        val wallet = requireNotNull(moneroWalletService.wallet) {
            "Monero hardware wallet is not initialized"
        }
        val restoreHeight = requireHardwareRestoreHeight()
        val mode = forcedMode ?: trezorRefreshMode(hardwareRestoreHeight(wallet.restoreHeight), restoreHeight)
        // Built first: a request the kit rejects must not leave a marker every later pass would repeat.
        val request = HardwareKeyImageRefreshResult.Request(mode, startProtocolEagerly = attended, restoreHeight)
        if (mode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight) {
            restoreSettingsManager.savePendingMoneroRescan(account, restoreHeight)
        }
        // A requested reset must survive an interrupted pass: the next one reads this and resets again.
        if (forcedMode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight) {
            saveSpentReconciliationState(MoneroSpentReconciliationState.ExplicitColdRecoveryPending)
        }
        trezorPassEarlyEnd = null
        trezorCheckpointAt = timeSource.markNow() + TREZOR_CHECKPOINT_INTERVAL
        val result = refreshHardwareKeyImagesWithProgress(
            cachedTotalHeight = moneroWalletService.daemonHeight,
            fallbackTotalHeight = { wallet.daemonBlockChainHeight },
            onNewBlock = { endTrezorPassEarlyIfRequested(attended, wallet::requestControlledRefreshStop) },
        ) {
            wallet.refreshWithHardwareKeyImages(request)
        }
        if (attended && result.protocolStarted && result.finalAckChecked) {
            moneroTrezorGateway.markLiveRefreshConfirmed(account)
        }
        if (result.needsStore(mode, storedChainHeight)) {
            storeControlledHardwareRefresh(wallet, mode)
            storedChainHeight = result.finalHeight
        }
        if (mode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight) {
            restoreSettingsManager.clearPendingMoneroRescan(account)
            saveSpentReconciliationState(MoneroSpentReconciliationState.LiveRefreshPending)
        }
        return result
    }

    private fun trezorRefreshMode(walletRestoreHeight: Long, restoreHeight: Long): HardwareKeyImageRefreshResult.Mode {
        val resetRequired =
            restoreSettingsManager.moneroSpentReconciliationState(account) in RESET_RECONCILIATION_STATES ||
                restoreSettingsManager.pendingMoneroRescanHeight(account) != null ||
                walletRestoreHeight != restoreHeight
        return if (resetRequired) {
            HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight
        } else {
            HardwareKeyImageRefreshResult.Mode.Continue
        }
    }

    private fun isReplacedByAttendedSync(attended: Boolean): Boolean =
        !attended && attendedSyncsWaiting.get() > 0

    /** The one decision whether a Trezor pass may open the wallet or touch the device; call under lifecycleMutex. */
    private fun admitsTrezorPass(attended: Boolean, cue: Long? = null): Boolean {
        val lifecycleAllows = !released && backgroundManager.inForeground && !networkPaused
        // A wallet open from disk, or one a failed close kept, has no online session to advance.
        val walletAllows = !(isStarted && isPaused)
        // An unattended pass holds the lock until its first batch ends, so a waiting interruption goes first.
        val yieldsToInterruption = !attended && interruptionsWaiting.get() > 0
        val cueUnanswered = cue == null || cue == attendedPassesFinished
        return lifecycleAllows && walletAllows && !yieldsToInterruption && !isReplacedByAttendedSync(attended) &&
            cueUnanswered
    }

    private fun saveSpentReconciliationState(state: MoneroSpentReconciliationState) {
        if (restoreSettingsManager.moneroSpentReconciliationState(account) != state) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(account, state)
        }
    }

    /**
     * Runs in the block callback on the refresh thread and must not throw back into JNI. Re-issued on
     * every observed block: a rescan runs a second wallet2 refresh, which resets an earlier stop.
     */
    internal fun endTrezorPassEarlyIfRequested(attended: Boolean, stopRefresh: () -> Unit) {
        val earlyEnd = requestedTrezorEarlyEnd(attended) ?: return
        trezorPassEarlyEnd = maxOf(trezorPassEarlyEnd ?: earlyEnd, earlyEnd)
        stopRefresh()
    }

    private fun requestedTrezorEarlyEnd(attended: Boolean): TrezorPassEarlyEnd? = when {
        // Read live: the lifecycle collector that would stop the wallet may itself wait on this pass.
        interruptionsWaiting.get() > 0 || !backgroundManager.inForeground || trezorDetached ->
            TrezorPassEarlyEnd.Interruption
        moneroTrezorGateway.isUserOperationWaiting -> TrezorPassEarlyEnd.UserYield
        isReplacedByAttendedSync(attended) -> TrezorPassEarlyEnd.UserYield
        trezorCheckpointAt?.hasPassedNow() == true -> TrezorPassEarlyEnd.Checkpoint
        else -> null
    }

    internal fun <T> refreshHardwareKeyImagesWithProgress(
        cachedTotalHeight: Long,
        fallbackTotalHeight: () -> Long,
        onNewBlock: () -> Unit,
        refresh: () -> T,
    ): T {
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
            return refresh()
        } finally {
            moneroWalletService.clearControlledRefreshProgressObserver()
        }
    }

    private fun requireHardwareRestoreHeight(): Long = hardwareRestoreHeight(
        restoreSettingsManager.pendingMoneroRescanHeight(account)
            ?: restoreSettingsManager.settings(account, BlockchainType.Monero).birthdayHeight
            ?.takeIf { it >= 0 }
            ?: error("Monero hardware wallet has no restore height"),
    )

    // A device reset rejects height 0; block 0 has no user outputs, so scanning from 1 is the same scan.
    private fun hardwareRestoreHeight(height: Long): Long = height.coerceAtLeast(1)

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

    // wallet2 may hold state corrupted by the failed device, so the wallet closes unsaved.
    private fun abortControlledHardwareWallet(error: Throwable) {
        deactivateReconciliationSession()
        publishTrezorSyncFailure(error)
        try {
            if (!closeNativeWallet(saveWallet = false)) {
                moneroWalletService.abandonFaultedWallet()
            }
        } catch (cleanupError: Throwable) {
            error.addSuppressed(cleanupError)
            moneroWalletService.abandonFaultedWallet()
        }
        isStarted = false
        isPaused = false
        storedChainHeight = null
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
        if (hardwareAccount) {
            rescanHardwareWallet(newHeight)
        } else {
            lifecycleMutex.withLock {
                resetWalletAndRestart(newHeight)
            }
        }
    }

    private suspend fun rescanHardwareWallet(newHeight: Long) {
        require(newHeight >= 0) { "Monero restore height must be non-negative" }
        withInterruptingLock {
            restoreSettingsManager.savePendingMoneroRescan(account, newHeight)
        }
        _syncState.value = AdapterState.Syncing()
        syncTrezorWallet(attended = true, HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight)
    }

    /** Ends a running pass at its next block; the device that answers the next session resets it. */
    fun onTrezorDetached() {
        trezorDetached = true
    }

    suspend fun refreshHardwareKeyImages() {
        syncTrezorWallet(attended = true)
    }

    /** A sync nobody asked for: it never prompts, and a Trezor pass gives the device up to a user operation. */
    suspend fun syncUnattended() {
        if (hardwareAccount) syncTrezorWalletLogged(attended = false) else refresh()
    }

    /** An unattended sync for the user's [cue]: an unconfirmed device is asked once, never again on failure. */
    suspend fun syncAskingDevice(cue: Long) {
        try {
            syncTrezorWallet(attended = false)
        } catch (error: HardwareWalletOperationException) {
            if (!error.isPendingDeviceVerification()) throw error
            syncTrezorWallet(attended = true, cue = cue)
        }
    }

    /** Asks the device for the user's [cue], read from [attendedPassesFinished] when the cue arrived. */
    suspend fun askDevice(cue: Long) = syncTrezorWalletLogged(attended = true, cue = cue)

    private suspend fun syncTrezorWalletLogged(attended: Boolean, cue: Long? = null) {
        try {
            syncTrezorWallet(attended, cue = cue)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warning("sync: Trezor pass failed", error)
            moneroLogger.e(error) { "Failed to sync Monero Trezor wallet" }
        }
    }

    private suspend fun syncTrezorWallet(
        attended: Boolean,
        forcedMode: HardwareKeyImageRefreshResult.Mode? = null,
        cue: Long? = null,
    ) {
        check(hardwareAccount) { "Monero device sync requires a hardware wallet" }
        if (attended) attendedSyncsWaiting.incrementAndGet()
        try {
            controlledRefreshCoordinator.execute(TrezorSyncOperation(attended, forcedMode, cue))
        } finally {
            if (attended) attendedSyncsWaiting.decrementAndGet()
        }
    }

    private inner class TrezorSyncOperation(
        override val attended: Boolean,
        private val forcedMode: HardwareKeyImageRefreshResult.Mode?,
        private val cue: Long?,
    ) : ControlledHardwareRefreshOperation {
        override val resets: Boolean
            get() = forcedMode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight

        override suspend fun run(aborter: ControlledHardwareRefreshAborter) = lifecycleMutex.withLock {
            if (!admitsTrezorPass(attended, cue)) return@withLock
            val credentials = if (moneroWalletService.wallet == null) resolveWalletCredentials() else null
            try {
                syncTrezorWalletSession(attended, forcedMode, aborter) {
                    credentials?.let(::openTrezorWallet)
                }
            } finally {
                if (attended) attendedPassesFinished++
            }
        }

        override fun abort(error: Throwable) {
            abortControlledHardwareWallet(error)
        }
    }

    /** A failure before the device answered leaves an open wallet NotSynced; one after it closes the wallet unsaved. */
    private suspend fun syncTrezorWalletSession(
        attended: Boolean,
        forcedMode: HardwareKeyImageRefreshResult.Mode?,
        aborter: ControlledHardwareRefreshAborter,
        open: () -> Unit,
    ) {
        val lagPassesBefore = nodeBehindStreak.also { nodeBehindStreak = 0 }
        val stateBefore = _syncState.value
        var reachedDevice = false
        try {
            withTrezor(attended) {
                reachedDevice = true
                trezorDetached = false
                // Going offline while the device waited for the user must not reach the node afterwards.
                if (requestedTrezorEarlyEnd(attended) == TrezorPassEarlyEnd.Interruption) return@withTrezor
                open()
                finishTrezorSync(syncTrezorWalletLeaseOwned(attended, forcedMode), stateBefore, lagPassesBefore)
            }
        } catch (error: CancellationException) {
            if (reachedDevice) aborter.abort(error)
            throw error
        } catch (error: Throwable) {
            if (reachedDevice) aborter.abort(error) else publishTrezorSyncFailure(error)
            throw error
        }
    }

    private suspend fun <T> withTrezor(attended: Boolean, block: (String) -> T): T =
        if (attended) {
            moneroTrezorGateway.execute(account, block)
        } else {
            moneroTrezorGateway.executeYielding(account, block)
        }

    /** A failed device pass or start needs a new attended preparation, unlike a pass that only ended early. */
    private fun publishTrezorSyncFailure(error: Throwable) {
        _spendReadiness.value = MoneroSpendReadiness.ReconciliationFailed
        _syncState.value = AdapterState.NotSynced(error)
    }

    private fun finishTrezorSync(
        result: HardwareKeyImageRefreshResult,
        stateBefore: AdapterState,
        lagPassesBefore: Int,
    ) {
        val wallet = requireNotNull(moneroWalletService.wallet) { "Monero hardware wallet is not initialized" }
        val session = requireActiveReconciliationSession()
        when {
            // Shown now rather than when the next pass finds no confirmed session.
            !result.completed && trezorDetached -> publishTrezorSyncFailure(
                HardwareWalletOperationException(HardwareWalletErrorCode.DeviceNotFound, "Trezor was detached"),
            )
            // Ended early: the next pass continues from the stored checkpoint.
            !result.completed -> setSpendReadinessForSession(session, MoneroSpendReadiness.Syncing)
            moneroScanReachedDaemon(
                result.finalHeight,
                wallet.daemonBlockChainHeight,
                wallet.daemonBlockChainTargetHeight,
            ) -> finalizeTrezorSync(session, wallet, result.finalHeight)
            else -> onTrezorNodeBehind(session, stateBefore, lagPassesBefore)
        }
    }

    private fun finalizeTrezorSync(session: Long, wallet: Wallet, height: Long) {
        wallet.setSynchronized()
        saveSpentReconciliationState(MoneroSpentReconciliationState.Ready)
        setLastBlockInfoForSession(session, LastBlockInfo(height.toInt()))
        publishHistory(session, wallet)
        setSpendReadinessForSession(session, MoneroSpendReadiness.Ready)
        setSyncStateForSession(session, AdapterState.Synced)
    }

    private fun publishHistory(session: Long, wallet: Wallet) {
        wallet.refreshHistory()
        emitTransactionsStateUpdatedForSession(session)
    }

    private fun onTrezorNodeBehind(session: Long, stateBefore: AdapterState, lagPassesBefore: Int) {
        nodeBehindStreak = lagPassesBefore + 1
        setSpendReadinessForSession(session, MoneroSpendReadiness.Syncing)
        val state = when {
            nodeBehindStreak >= NODE_BEHIND_PASSES_BEFORE_SHOWN -> AdapterState.NotSynced(
                HardwareWalletOperationException(HardwareWalletErrorCode.Network, "Monero node is not synchronized"),
            )
            stateBefore is AdapterState.Synced -> stateBefore
            else -> AdapterState.Syncing()
        }
        setSyncStateForSession(session, state)
    }

    internal fun openPausedHardwareWalletForRefresh(start: () -> Unit) {
        val previousStartInProgress = startInProgress
        startInProgress = true
        try {
            activateReconciliationSession()
            storedChainHeight = null
            start()
            isStarted = true
            isPaused = false
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

        moneroFileDao?.deleteAssociatedRecord(account.id)

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

    suspend fun stop(saveWallet: Boolean = true) = withInterruptingLock {
        stopInternal(saveWallet)
    }

    // The wallet service is shared across accounts, so a pass queued before this must not reach it afterwards.
    suspend fun release() = withInterruptingLock {
        stopInternal()
        released = true
    }

    /** A running Trezor pass ends at its next batch boundary and stores its progress before [block] runs. */
    private suspend fun <T> withInterruptingLock(block: suspend () -> T): T {
        interruptionsWaiting.incrementAndGet()
        try {
            lifecycleMutex.lock()
        } finally {
            interruptionsWaiting.decrementAndGet()
        }
        try {
            return block()
        } finally {
            lifecycleMutex.unlock()
        }
    }

    internal suspend fun <T> withNativeWalletReleased(
        localOnly: Boolean,
        block: suspend () -> T,
    ): T = withInterruptingLock {
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
                check(isStarted || staysClosed(localOnly)) { "Failed to restore the active Monero wallet" }
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
                val closed = closeNativeWallet(saveWallet)
                if (hardwareAccount && !closed) {
                    isPaused = true
                    throw moneroWalletService.wallet?.status.toHardwareWalletCloseFailure(
                        "Failed to close Monero hardware wallet",
                    )
                }
                isStarted = false
                isPaused = false
                openedWithoutNode = false
                storedChainHeight = null
                lastLoggedSyncProgress = -1
                lastLoggedConnectionStatus = null
                if (hardwareAccount) {
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

    suspend fun pause() = withInterruptingLock {
        networkPaused = true
        if (isStarted && !isPaused) {
            logger.info("pause: pausing wallet refresh")
            moneroWalletService.pause()
            isPaused = true
            logger.info("pause: done")
        } else {
            logger.info("pause: skip, isStarted=$isStarted isPaused=$isPaused")
        }
    }

    // A Trezor wallet has no native refresh thread to resume: its passes run under the device.
    suspend fun resume(): Boolean = lifecycleMutex.withLock {
        if (hardwareAccount) isNetworkOnline else resumeAttachingNode()
    }

    // A failed connect still initializes the daemon, so a local resume would look online without a node.
    private suspend fun resumeAttachingNode(): Boolean =
        if (openedWithoutNode) connectDaemonAndResume() else resumeInternal()

    val isNetworkOnline: Boolean
        get() = isStarted && !isPaused

    /** Offline mode's view: a Trezor wallet waiting for its next device pass is closed, yet online. */
    val isNetworkEnabled: Boolean
        get() = if (hardwareAccount) !networkPaused else isNetworkOnline

    suspend fun resumeNetwork(): Boolean {
        if (hardwareAccount) return resumeTrezorNetwork()
        return lifecycleMutex.withLock {
            if (!isStarted || !isPaused) return@withLock isStarted
            connectDaemonAndResume()
        }
    }

    // A user action: the attended pass may ask for confirmation, which an empty session cache needs.
    // It may run for minutes, so it is not awaited; its failure shows as NotSynced, not as offline again.
    private suspend fun resumeTrezorNetwork(): Boolean {
        lifecycleMutex.withLock {
            if (isNetworkOnline) return true
            // A failure here leaves networkPaused set and the wallet closed, which is the offline state.
            stopInternal(saveWallet = false)
            // A wallet that never opened online in this process has no daemon, and the online open needs one.
            daemonSelector.selectDaemon()
            networkPaused = false
        }
        passScope.launch { syncTrezorWalletLogged(attended = true) }
        return true
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
    suspend fun saveSynced(): Boolean = lifecycleMutex.withLock {
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
    }

    // A Trezor pass stores its own progress under the device.
    private fun shouldSaveSyncedWallet(): Boolean =
        !hardwareAccount &&
            isStarted &&
            !isPaused &&
            !storedForSync &&
            _syncState.value is AdapterState.Synced

    suspend fun refresh() {
        if (hardwareAccount) syncTrezorWalletLogged(attended = true) else refreshSoftwareWallet()
    }

    private suspend fun refreshSoftwareWallet() = lifecycleMutex.withLock {
        if (_syncState.value is AdapterState.Syncing) {
            logger.info("refresh: skip, already syncing")
            moneroLogger.d { "MoneroKitWrapper: Already syncing, skipping refresh" }
            return@withLock
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
    ): String = withInterruptingLock {
        withContext(Dispatchers.IO) {
            if (hardwareAccount) {
                // This intentionally touches no native wallet state: do it before pause/drain
                // and before acquiring a hardware-device session.
                requireHardwareSendLifecycleReady()
                val wallet = moneroWalletService.wallet
                    ?: throw IllegalStateException("Monero wallet not initialized")
                sendHardware(wallet, amount, address, memo)
            } else {
                val wallet = moneroWalletService.wallet
                    ?: throw IllegalStateException("Monero wallet not initialized")
                val txData = buildTxData(amount, address, memo, wallet)
                moneroWalletService.prepareTransaction(txData)
                moneroWalletService.sendTransaction(memo)
            }
        }
    }

    private suspend fun sendHardware(
        wallet: Wallet,
        amount: BigDecimal,
        address: String,
        memo: String?,
    ): String {
        var prepared = false
        val txId = try {
            moneroTrezorGateway.execute(account) {
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
        // No native resume follows any more to show the sent tx; it is already broadcast, so this must not throw.
        try {
            activeReconciliationSession()?.let { publishHistory(it, wallet) }
        } catch (error: Exception) {
            moneroLogger.w(error) { "History refresh after a hardware send failed" }
        }
        return txId
    }

    /** Full wallet recovery: a rescan from the restore height under the device, kept across restarts until stored. */
    suspend fun syncKeyImages() {
        check(hardwareAccount) { "Key image sync requires a hardware wallet" }
        syncTrezorWallet(attended = true, HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight)
    }

    suspend fun displayAddressOnDevice(addressIndex: Int) = withInterruptingLock {
        withContext(dispatcherProvider.io) {
            require(addressIndex >= 0) { "Monero address index must be non-negative" }
            check(hardwareAccount) { "Address display requires a hardware wallet" }
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            moneroTrezorGateway.execute(account) {
                wallet.deviceShowAddress(0, addressIndex, "")
            }
        }
    }

    private fun abandonFaultedWallet(failure: HardwareWalletOperationException) {
        deactivateReconciliationSession()
        moneroWalletService.abandonFaultedWallet()
        isStarted = false
        isPaused = false
        _syncState.value = AdapterState.NotSynced(failure)
        if (hardwareAccount) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    private suspend fun recoverFaultedWallet(): Throwable? {
        return try {
            startInternal(fixIfCorruptedFile = true)
            null
        } catch (error: Throwable) {
            error
        }
    }

    // Held under lifecycleMutex so a background stop()/saveSynced() cannot race a raw-tx sign/submit.
    suspend fun createSignedRawTransaction(
        amount: BigDecimal,
        address: String,
        memo: String?,
    ): SignedRawMoneroTransaction = lifecycleMutex.withLock {
        withContext(Dispatchers.IO) {
            check(!hardwareAccount) { "Offline signing is unavailable for Trezor Monero wallets" }
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            moneroWalletService.createSignedRawTransaction(buildTxData(amount, address, memo, wallet))
        }
    }

    // Held under lifecycleMutex so a background stop()/saveSynced() cannot race a raw-tx sign/submit.
    suspend fun submitSignedRawTransaction(raw: ByteArray): RawMoneroBroadcastResult =
        withInterruptingLock {
            withContext(Dispatchers.IO) {
                if (hardwareAccount) {
                    requireHardwareSendLifecycleReady()
                    val wallet = moneroWalletService.wallet
                        ?: throw IllegalStateException("Monero wallet not initialized")
                    requireHardwareSendReady(wallet, requireCurrentWallet = true)
                    moneroWalletService.submitSignedRawTransaction(raw)
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
        check(walletHealthReader.snapshot(wallet).isNativelyHealthy) {
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
    ): Long = withInterruptingLock {
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

    // Every native close goes through here so getBalance() can void reads that overlap it.
    private fun closeNativeWallet(saveWallet: Boolean): Boolean = try {
        nativeCloseSequence++
        moneroWalletService.stop(saveWallet)
    } finally {
        nativeCloseSequence++
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

    suspend fun getSubaddresses(): List<MoneroSubaddressInfo> = withInterruptingLock {
        withContext(Dispatchers.IO) {
            val wallet = moneroWalletService.wallet ?: return@withContext emptyList()
            val count = wallet.numSubaddresses
            (0 until count).map { index ->
                val subaddress = wallet.getSubaddressObject(index)
                MoneroSubaddressInfo(
                    index = subaddress.addressIndex,
                    address = subaddress.address,
                    receivedAmount = subaddress.amount
                )
            }
        }
    }

    suspend fun createNewSubaddress(): String = withInterruptingLock {
        withContext(Dispatchers.IO) {
            val wallet = moneroWalletService.wallet
                ?: throw IllegalStateException("Monero wallet not initialized")
            wallet.newSubaddress
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
        // A Trezor wallet's service stays paused: its own device passes publish every state.
        if (hardwareAccount) return true
        val session = activeReconciliationSession() ?: return false
        if (!isStarted) return false
        val health = try {
            walletHealthReader.snapshot(wallet)
        } catch (error: Throwable) {
            failClosedAfterPostSyncError(session, error)
            return true
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

        if (hasMoneroNativeHealthFailure(health)) {
            lastLoggedSyncProgress = -1
            setSyncStateForSession(
                session,
                AdapterState.NotSynced(nativeHealthFailureError(health)),
            )
            return true
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

        setSyncStateForSession(
            session,
            resolveSyncState(nativeConnected, isSynchronized, currentHeight, totalHeight),
        )
        return true
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
        // A Trezor wallet opens paused; the device pass that follows publishes its state.
        if (hardwareAccount) return
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
    }

    private fun failClosedAfterPostSyncError(session: Long, error: Throwable) {
        // Do not inspect JNI state while failing closed: the callback may be stale.
        setSpendReadinessForSession(session, MoneroSpendReadiness.ReconciliationFailed)
        moneroLogger.e(error) { "Monero spent-status reconciliation failed" }
    }

    private fun activeReconciliationSession(): Long? = spentStatusReconciler.activeSession()

    private fun requireActiveReconciliationSession(): Long =
        checkNotNull(spentStatusReconciler.activeSession()) {
            "Monero reconciliation session is not active"
        }

    private fun setSpendReadinessForSession(
        session: Long,
        readiness: MoneroSpendReadiness,
    ) {
        if (!spentStatusReconciler.isActive(session)) return
        _spendReadiness.value = readiness
        if (!spentStatusReconciler.isActive(session)) {
            _spendReadiness.compareAndSet(readiness, MoneroSpendReadiness.Syncing)
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
        if (hardwareAccount) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    private fun deactivateReconciliationSession() {
        spentStatusReconciler.deactivate()
        if (hardwareAccount) {
            _spendReadiness.value = MoneroSpendReadiness.Syncing
        }
    }

    override fun onWalletOpen(device: Wallet.Device?) {
        moneroLogger.d { "onWalletOpen: $device" }
    }
}

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

internal enum class TrezorPassEarlyEnd { Checkpoint, UserYield, Interruption }

private val RESET_RECONCILIATION_STATES = setOf(
    MoneroSpentReconciliationState.MigrationReplayRequired,
    MoneroSpentReconciliationState.MigrationReplayPending,
    MoneroSpentReconciliationState.ExplicitColdRecoveryPending,
)

/** A pass that changed nothing is not stored again: periodic passes mostly find no new block. */
internal fun HardwareKeyImageRefreshResult.needsStore(
    mode: HardwareKeyImageRefreshResult.Mode,
    storedChainHeight: Long?,
): Boolean =
    suffixDetached ||
        finalHeight != storedChainHeight ||
        mode == HardwareKeyImageRefreshResult.Mode.ResetToRestoreHeight ||
        !completed
