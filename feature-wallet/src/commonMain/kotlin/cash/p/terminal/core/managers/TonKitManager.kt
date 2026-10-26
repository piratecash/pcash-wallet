package cash.p.terminal.core.managers

import cash.p.terminal.core.onPollingStarted
import cash.p.terminal.core.onPollingStopped
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.core.toFixedSize
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import org.koin.java.KoinJavaComponent.get
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.SyncState
import io.horizontalsystems.tronkit.hexStringToByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import org.ton.kotlin.crypto.PublicKeyEd25519
import co.touchlab.kermit.Logger
import java.util.concurrent.atomic.AtomicInteger

private val logger = Logger.withTag("TonKitManager")

class TonKitManager(
    private val backgroundManager: IBackgroundManager,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val platformContexts: KitPlatformContexts,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val networkErrorTracker: NetworkErrorTracker,
    private val offlineModeManager: OfflineModeManager,
    private val tonKitDatabaseKeyProvider: TonKitDatabaseKeyProvider,
    private val deviceSigners: DeviceSignerFactory,
) {
    private val lifecycleMutex = Mutex()
    private val pollingSessionCount = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private val _kitStartedFlow = MutableStateFlow(false)
    val kitStartedFlow: StateFlow<Boolean> = _kitStartedFlow

    var tonKitWrapper: TonKitWrapper? = null
        private set(value) {
            field = value

            _kitStartedFlow.update { value != null }
        }

    private var useCount = 0
    var currentAccount: Account? = null
        private set

    val statusInfo: Map<String, Any>?
        get() = networkErrorTracker.mergedStatusInfo(
            tonKitWrapper?.tonKit?.statusInfo(),
            BlockchainType.Ton,
            currentAccount?.id,
        )

    suspend fun getTonKitWrapper(
        account: Account,
        blockchainType: BlockchainType?,
    ): TonKitWrapper = lifecycleMutex.withLock {
        if (this.tonKitWrapper != null && currentAccount != account) {
            stop()
        }

        if (this.tonKitWrapper == null) {
            val wrapper = createKitInstance(getTonWallet(account, blockchainType), account)
            this.tonKitWrapper = wrapper
            job = scope.launch {
                start(account, wrapper)
            }
            useCount = 0
            currentAccount = account
        }

        useCount++
        requireNotNull(this.tonKitWrapper)
    }

    suspend fun getTonWallet(
        account: Account,
        blockchainType: BlockchainType?,
    ): TonWallet = when (val type = account.type) {
        is AccountType.HardwareDevice -> deviceTonWallet(account, type)
        else -> account.toTonWallet(hardwarePublicKeyStorage, blockchainType)
    }

    // Jettons share the TON key, so whichever of the chain's keys was stored first serves.
    private suspend fun deviceTonWallet(account: Account, type: AccountType.HardwareDevice): TonWallet {
        val key = hardwarePublicKeyStorage.getKeyByBlockchain(account.id, BlockchainType.Ton)
            ?: throw UnsupportedException("Hardware device does not have a key for TON")
        val publicKey = PublicKeyEd25519(ByteString(key.key.value.hexStringToByteArray()))
        return TonWallet.ExternalSigner(publicKey, deviceSigners.ton(account, type, key))
    }

    // Under the lifecycle lock so it cannot interleave with clear() of the same account.
    suspend fun getNonActiveTonKitWrapper(
        account: Account,
        blockchainType: BlockchainType?,
    ) = lifecycleMutex.withLock {
        createKitInstance(getTonWallet(account, blockchainType), account)
    }

    private fun eventListenerFactory(account: Account): NetworkErrorEventListener.Factory =
        NetworkErrorEventListener.Factory(BlockchainType.Ton, account.id, networkErrorTracker)

    // Takes a TonWallet, so only supported account types reach the database key.
    private suspend fun createKitInstance(
        tonWallet: TonWallet,
        account: Account,
    ): TonKitWrapper {
        return TonKitWrapper(
            TonKit.getInstance(
                tonWallet,
                Network.MainNet,
                platformContexts.ton,
                account.id,
                databaseKey = prepareDatabase(account),
                eventListenerFactory = eventListenerFactory(account),
            ),
            tonWallet
        )
    }

    private suspend fun prepareDatabase(account: Account): ByteArray {
        val databaseKey = tonKitDatabaseKeyProvider.awaitKey(account.id)
        TonKit.migrateDatabase(platformContexts.ton, Network.MainNet, account.id, databaseKey)
        return databaseKey
    }

    suspend fun clear(accountId: String) = lifecycleMutex.withLock {
        if (currentAccount?.id == accountId) {
            stop()
        }
        Network.entries.forEach { TonKit.clear(platformContexts.ton, it, accountId) }
        tonKitDatabaseKeyProvider.remove(accountId)
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
                tonKitWrapper?.let { wrapper ->
                    wrapper.startNetwork()
                    wrapper.tonKit.refresh()
                }
            }
        }
    }

    suspend fun stopForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStopped(backgroundManager) {
            tonKitWrapper?.stopNetwork()
        }
    }

    suspend fun pauseNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        val wrapper = tonKitWrapper ?: return@withLock
        if (!wrapper.networkStarted) return@withLock
        wrapper.stopNetwork()
    }

    suspend fun resumeNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        val wrapper = tonKitWrapper ?: return@withLock
        if (wrapper.networkStarted) return@withLock
        wrapper.startNetwork()
        wrapper.tonKit.refresh()
    }

    suspend fun refresh() = lifecycleMutex.withLock {
        val account = currentAccount ?: return@withLock
        if (isNetworkPaused(account)) return@withLock
        tonKitWrapper?.tonKit?.refresh()
    }

    // Ownership is invalidated before the suspending teardown, so a cancellation here cannot leave a
    // half-stopped wrapper published. The teardown runs under NonCancellable and cancels the start
    // job first: a start still in flight would otherwise resurrect the SSE listener, which lives in
    // its own scope and outlives this job.
    private suspend fun stop() {
        val stoppingJob = job
        val wrapper = tonKitWrapper
        job = null
        tonKitWrapper = null
        currentAccount = null
        withContext(NonCancellable) {
            stoppingJob?.cancelAndJoin()
            wrapper?.tonKit?.stop()
        }
        // NonCancellable does not rethrow on exit, so without this a cancelled account switch would
        // go on to create and start the abandoned account's kit.
        currentCoroutineContext().ensureActive()
    }

    // Callers hold lifecycleMutex to keep networkStarted consistent with the kit's listener state.
    private suspend fun TonKitWrapper.startNetwork() {
        tonKit.startListener()
        networkStarted = true
    }

    private suspend fun TonKitWrapper.stopNetwork() = withContext(NonCancellable) {
        tonKit.stop()
        networkStarted = false
    }

    private fun isNetworkPaused(account: Account): Boolean =
        offlineModeManager.isNetworkPaused(account, BlockchainType.Ton)

    // Runs inside `job` and drives the kit it was created with: a later account switch cancels this
    // job, so the gate can never be evaluated for one account against another account's kit.
    private suspend fun start(account: Account, wrapper: TonKitWrapper) {
        logger.d { "TonKitManager start" }
        val kit = wrapper.tonKit
        if (!isNetworkPaused(account)) {
            lifecycleMutex.withLock { wrapper.startNetwork() }
            kit.refresh()
        }
        backgroundManager.stateFlow.collect { state ->
            if (state == BackgroundManagerState.EnterForeground) {
                logger.d { "TonKitManager EnterForeground" }
                if (!isNetworkPaused(account)) {
                    delay(1000)
                    if (!isNetworkPaused(account)) kit.refresh()
                }
            } else if (state == BackgroundManagerState.EnterBackground) {
                if (pollingSessionCount.get() == 0 && !backgroundKeepAliveManager.isKeepAlive(BlockchainType.Ton)) {
                    lifecycleMutex.withLock { wrapper.stopNetwork() }
                } else {
                    Logger.withTag("TxPoller").d { "TonKit staying alive" }
                }
            }
        }
    }
}

class TonKitWrapper(val tonKit: TonKit, val tonWallet: TonWallet) {
    /** True once [TonKit.startListener] has been called and no matching [TonKit.stop] followed it. */
    var networkStarted: Boolean = false
}

fun TonKit.statusInfo() = buildMap {
    put("Sync State", syncStateFlow.value.toAdapterState())
    put("Event Sync State", eventSyncStateFlow.value.toAdapterState())
    put("Jetton Sync State", jettonSyncStateFlow.value.toAdapterState())
}

val Jetton.tokenType
    get() = TokenType.Jetton(address.toUserFriendly(true))

fun SyncState.toAdapterState(): AdapterState = when (this) {
    is SyncState.NotSynced -> AdapterState.NotSynced(error)
    is SyncState.Synced -> AdapterState.Synced
    is SyncState.Syncing -> AdapterState.Syncing()
}

fun Account.toTonWalletFullAccess(
    hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    blockchainType: BlockchainType?,
): TonWallet.FullAccess {
    val toTonWallet = toTonWallet(hardwarePublicKeyStorage, blockchainType)

    return toTonWallet as? TonWallet.FullAccess ?: throw IllegalArgumentException("Watch Only")
}

fun Account.toTonWallet(
    hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    blockchainType: BlockchainType?,
) = when (this.type) {
    is AccountType.Mnemonic -> {
        val hdWallet = HDWallet(
            (this.type as AccountType.Mnemonic).seed,
            607,
            HDWallet.Purpose.BIP44,
            Curve.Ed25519
        )
        val privateKey = hdWallet.privateKey(0)
        TonWallet.Seed(privateKey.privKeyBytes.toFixedSize(32))
    }

    is AccountType.TonAddress -> {
        TonWallet.WatchOnly((this.type as AccountType.TonAddress).address)
    }

    is AccountType.HardwareCard -> {
        runBlocking {
            val resolvedBlockchainType = blockchainType
                ?: throw IllegalArgumentException("Blockchain type is null")
            // All TON tokens share the same key/address (derivation path m/44'/607'/0').
            // Try Native first, then fall back to any available key for this blockchain
            // (covers case where only Jetton tokens were added without native TON).
            val hardwarePublicKey = hardwarePublicKeyStorage.getKey(
                accountId = id,
                blockchainType = resolvedBlockchainType,
                tokenType = TokenType.Native
            ) ?: hardwarePublicKeyStorage.getAllPublicKeys(id).firstOrNull {
                it.blockchainType == resolvedBlockchainType.uid
            }
            if (hardwarePublicKey == null || hardwarePublicKey.type != HardwarePublicKeyType.ADDRESS) {
                throw UnsupportedException(
                    "Hardware public key not found for TON (accountId=$id, blockchainType=$resolvedBlockchainType)"
                )
            }
            TonWallet.FullAccess(
                PublicKeyEd25519(ByteString(hardwarePublicKey.key.value.hexStringToByteArray())),
                get<TonHardwareSigners>(TonHardwareSigners::class.java).cardHashSigner(hardwarePublicKey)
            )
        }
    }

    else -> throw IllegalArgumentException(
        "Account type ${this.javaClass.simpleName} can not be converted to TonKit.WalletType"
    )
}
