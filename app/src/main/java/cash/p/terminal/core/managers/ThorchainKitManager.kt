package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.onPollingStarted
import cash.p.terminal.core.onPollingStopped
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.DatabaseKeyMismatchException
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.network.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class ThorchainKitManager(
    private val network: Network,
    val blockchainType: BlockchainType,
    private val thornodeUrls: List<URL>,
    private val context: Context,
    private val kitDatabaseKeys: KitDatabaseKeys,
    private val backgroundManager: BackgroundManager,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val networkErrorTracker: NetworkErrorTracker,
    private val offlineModeManager: OfflineModeManager,
) {
    private val lifecycleMutex = Mutex()
    private val pollingSessionCount = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private val _kitStartedFlow = MutableStateFlow(false)
    val kitStartedFlow: StateFlow<Boolean> = _kitStartedFlow

    var thorchainKitWrapper: ThorchainKitWrapper? = null
        private set(value) {
            field = value

            _kitStartedFlow.update { value != null }
        }

    private var useCount = 0
    var currentAccount: Account? = null
        private set

    val statusInfo: Map<String, Any>?
        get() = networkErrorTracker.mergedStatusInfo(
            thorchainKitWrapper?.thorchainKit?.statusInfo(),
            blockchainType,
            currentAccount?.id,
        )

    suspend fun getThorchainKitWrapper(account: Account): ThorchainKitWrapper =
        lifecycleMutex.withLock {
            if (this.thorchainKitWrapper != null && currentAccount != account) {
                stop()
            }

            if (this.thorchainKitWrapper == null) {
                val wrapper = ThorchainKitWrapper(createKit(account))
                this.thorchainKitWrapper = wrapper
                job = scope.launch {
                    start(account, wrapper)
                }
                useCount = 0
                currentAccount = account
            }

            useCount++
            requireNotNull(this.thorchainKitWrapper)
        }

    suspend fun unlink(account: Account) = lifecycleMutex.withLock {
        if (account == currentAccount) {
            useCount -= 1

            if (useCount < 1) {
                stop()
            }
        }
    }

    suspend fun startForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStarted {
            val account = currentAccount
            if (account == null || !isNetworkPaused(account)) {
                thorchainKitWrapper?.let { wrapper ->
                    startNetwork(wrapper)
                    wrapper.thorchainKit.refresh()
                }
            }
        }
    }

    suspend fun stopForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStopped(backgroundManager) {
            thorchainKitWrapper?.let(::stopNetwork)
        }
    }

    suspend fun pauseNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        val wrapper = thorchainKitWrapper ?: return@withLock
        if (!wrapper.networkStarted) return@withLock
        stopNetwork(wrapper)
    }

    suspend fun resumeNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        val wrapper = thorchainKitWrapper ?: return@withLock
        if (wrapper.networkStarted) return@withLock
        startNetwork(wrapper)
    }

    // Ownership is invalidated before the suspending teardown, so a cancellation here cannot leave a
    // half-destroyed wrapper published. The teardown runs under NonCancellable and cancels the start
    // job first: a start still in flight would otherwise bring the discarded account's networking
    // back up after teardown.
    private suspend fun stop() {
        val stoppingJob = job
        val wrapper = thorchainKitWrapper
        job = null
        thorchainKitWrapper = null
        currentAccount = null
        withContext(NonCancellable) {
            stoppingJob?.cancelAndJoin()
            wrapper?.let(::stopNetwork)
        }
        // NonCancellable does not rethrow on exit, so without this a cancelled account switch would
        // go on to create and start the abandoned account's kit.
        currentCoroutineContext().ensureActive()
    }

    private fun isNetworkPaused(account: Account): Boolean =
        offlineModeManager.isNetworkPaused(account, blockchainType)

    // Runs inside `job` and drives the kit it was created with: a later account switch cancels this
    // job, so the gate can never be evaluated for one account against another account's kit.
    // Each decision is re-read under lifecycleMutex, so a pause or polling cleanup cannot land between
    // the check and its start/stop.
    private suspend fun start(account: Account, wrapper: ThorchainKitWrapper) {
        lifecycleMutex.withLock { startIfOnline(account, wrapper) }
        backgroundManager.stateFlow.collect { state ->
            if (state == BackgroundManagerState.EnterForeground) {
                if (lifecycleMutex.withLock { isForeground() && startIfOnline(account, wrapper) }) {
                    delay(1000)
                    lifecycleMutex.withLock { if (!isNetworkPaused(account)) wrapper.thorchainKit.refresh() }
                }
            } else if (state == BackgroundManagerState.EnterBackground) {
                lifecycleMutex.withLock { stopIfIdleInBackground(wrapper) }
            }
        }
    }

    private fun isForeground() = backgroundManager.stateFlow.value == BackgroundManagerState.EnterForeground

    private fun startIfOnline(account: Account, wrapper: ThorchainKitWrapper): Boolean {
        if (isNetworkPaused(account)) return false
        startNetwork(wrapper)
        return true
    }

    private fun stopIfIdleInBackground(wrapper: ThorchainKitWrapper) {
        if (isForeground()) return
        if (pollingSessionCount.get() == 0 && !backgroundKeepAliveManager.isKeepAlive(blockchainType)) {
            stopNetwork(wrapper)
        } else {
            Timber.tag("TxPoller").d("ThorchainKit ${blockchainType.uid} staying alive")
        }
    }

    // The fee request goes through the kit's node failover, so it lives and dies with the kit's network.
    private fun startNetwork(wrapper: ThorchainKitWrapper) {
        wrapper.thorchainKit.start()
        wrapper.networkStarted = true
        if (wrapper.feeJob?.isActive != true) {
            wrapper.feeJob = scope.launch { fetchNativeFee(wrapper) }
        }
    }

    private fun stopNetwork(wrapper: ThorchainKitWrapper) {
        wrapper.feeJob?.cancel()
        wrapper.feeJob = null
        wrapper.thorchainKit.stop()
        wrapper.networkStarted = false
    }

    private suspend fun fetchNativeFee(wrapper: ThorchainKitWrapper) {
        try {
            wrapper.nativeFeeState.value = wrapper.thorchainKit.estimateFee()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "ThorchainKit ${blockchainType.uid} fee estimate failed")
        }
    }

    internal suspend fun createKit(account: Account): ThorchainKit {
        val seed = account.type.thorchainSeed()
        val databaseKey = kitDatabaseKeys.awaitKey(account.id)
        try {
            return try {
                getInstance(seed, account, databaseKey)
            } catch (_: DatabaseKeyMismatchException) {
                // Only a network cache lives there, so it is dropped and resynced under the current key.
                clear(account.id)
                getInstance(seed, account, databaseKey)
            }
        } finally {
            databaseKey.fill(0)
        }
    }

    fun getAddress(account: Account): String =
        ThorchainKit.getAddress(account.type.thorchainSeed(), network).toString()

    fun clear(accountId: String) = ThorchainKit.clear(context, network, accountId)

    private fun getInstance(seed: ByteArray, account: Account, databaseKey: ByteArray) =
        ThorchainKit.getInstance(
            context,
            seed,
            network,
            account.id,
            databaseKey,
            SYNC_INTERVAL_SECONDS,
            thornodeUrls,
            network.midgardUrls,
            NetworkErrorEventListener.Factory(blockchainType, account.id, networkErrorTracker),
        )

    private companion object {
        const val SYNC_INTERVAL_SECONDS = 15L
    }
}

internal fun AccountType.thorchainSeed(): ByteArray = when (this) {
    is AccountType.Mnemonic -> seed

    is AccountType.BitcoinAddress,
    is AccountType.EvmAddress,
    is AccountType.EvmPrivateKey,
    is AccountType.HardwareCard,
    is AccountType.HdExtendedKey,
    is AccountType.MnemonicMonero,
    is AccountType.SolanaAddress,
    is AccountType.StellarAddress,
    is AccountType.StellarSecretKey,
    is AccountType.TonAddress,
    is AccountType.TrezorDevice,
    is AccountType.TronAddress,
    is AccountType.ZCashUfvKey -> throw UnsupportedAccountException()
}

fun ThorchainKit.SyncState.toAdapterState(): AdapterState = when (this) {
    is ThorchainKit.SyncState.Synced -> AdapterState.Synced
    is ThorchainKit.SyncState.NotSynced -> AdapterState.NotSynced(error)
    is ThorchainKit.SyncState.Syncing -> AdapterState.Syncing()
}

class ThorchainKitWrapper(val thorchainKit: ThorchainKit) {
    /** True once [ThorchainKit.start] has been called and no matching [ThorchainKit.stop] followed it. */
    var networkStarted: Boolean = false
    internal var feeJob: Job? = null
    internal val nativeFeeState = MutableStateFlow(thorchainKit.network.defaultNativeFee)

    /** Plain-send fee in native base units; the network default until the node has reported it. */
    val nativeFee: StateFlow<BigInteger> = nativeFeeState.asStateFlow()
}

// NativeTransactionFee as documented by the kit, used until the node reports the current value.
private val Network.defaultNativeFee: BigInteger
    get() = when (this) {
        Network.Mainnet,
        Network.Stagenet -> BigInteger.valueOf(2_000_000)
        Network.MayaMainnet -> BigInteger.valueOf(2_000_000_000)
    }
