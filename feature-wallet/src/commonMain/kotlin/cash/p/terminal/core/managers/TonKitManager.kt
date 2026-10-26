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
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenType
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.tonkit.core.TonHttpClient
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.core.TonWalletVersion as KitTonWalletVersion
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
) {
    private val lifecycleMutex = Mutex()
    private val pollingSessionCount = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _startedKitsFlow = MutableStateFlow<Map<TonWalletVersion, TonKitWrapper>>(emptyMap())

    /** Kits of [currentAccount], one per wallet version in use. */
    val startedKitsFlow: StateFlow<Map<TonWalletVersion, TonKitWrapper>> = _startedKitsFlow

    // Written only under lifecycleMutex; replaced as a whole so lock-free readers see a consistent map.
    @Volatile
    private var entries: Map<TonWalletVersion, Entry> = emptyMap()
        set(value) {
            field = value
            _startedKitsFlow.value = value.mapValues { it.value.wrapper }
        }

    // Shared by the kits of the current account: one API key rotation state and connection pool.
    private var httpClient: TonHttpClient? = null

    var currentAccount: Account? = null
        private set

    val statusInfo: Map<String, Any>?
        get() = networkErrorTracker.mergedStatusInfo(
            kitsStatusInfo(),
            BlockchainType.Ton,
            currentAccount?.id,
        )

    private fun kitsStatusInfo(): Map<String, Any>? = entries.takeIf { it.isNotEmpty() }?.let { kits ->
        buildMap {
            kits.forEach { (version, entry) ->
                entry.wrapper.tonKit.statusInfo().forEach { (key, value) -> put("${version.title} $key", value) }
            }
        }
    }

    suspend fun getTonKitWrapper(
        account: Account,
        version: TonWalletVersion,
        blockchainType: BlockchainType?,
    ): TonKitWrapper = lifecycleMutex.withLock {
        if (entries.isNotEmpty() && currentAccount != account) {
            stop(entries.keys)
        }

        val entry = entries[version] ?: createEntry(account, version, blockchainType)
        entry.useCount++
        entry.wrapper
    }

    fun getTonWallet(
        account: Account,
        version: TonWalletVersion,
        blockchainType: BlockchainType?,
    ) = account.toTonWallet(
        version,
        hardwarePublicKeyStorage,
        blockchainType,
    )

    /** Derives the key, so call it off the main thread. */
    fun getReceiveAddress(account: Account, version: TonWalletVersion): String =
        getTonWallet(account, version, BlockchainType.Ton).address(Network.MainNet).toUserFriendly(false)

    // Under the lifecycle lock so it cannot interleave with clear() of the same account.
    suspend fun getNonActiveTonKitWrapper(
        account: Account,
        version: TonWalletVersion,
        blockchainType: BlockchainType?,
    ) = lifecycleMutex.withLock {
        val tonWallet = getTonWallet(account, version, blockchainType)
        createKitInstance(account, version, tonWallet, createHttpClient(account))
    }

    fun isNetworkStarted(account: Account): Boolean =
        account == currentAccount && entries.values.any { it.wrapper.networkStarted }

    private suspend fun createEntry(
        account: Account,
        version: TonWalletVersion,
        blockchainType: BlockchainType?,
    ): Entry {
        val tonWallet = getTonWallet(account, version, blockchainType)
        val client = httpClient ?: createHttpClient(account)
        val wrapper = createKitInstance(account, version, tonWallet, client)
        val entry = Entry(wrapper, scope.launch { start(account, wrapper) })
        httpClient = client
        currentAccount = account
        entries = entries + (version to entry)
        return entry
    }

    private fun createHttpClient(account: Account): TonHttpClient = TonKit.createHttpClient(
        eventListenerFactory = NetworkErrorEventListener.Factory(BlockchainType.Ton, account.id, networkErrorTracker),
    )

    // Takes a TonWallet, so only supported account types reach the database key.
    private suspend fun createKitInstance(
        account: Account,
        version: TonWalletVersion,
        tonWallet: TonWallet,
        httpClient: TonHttpClient,
    ): TonKitWrapper {
        val walletId = tonKitWalletId(account.id, version)
        return TonKitWrapper(
            TonKit.getInstance(
                tonWallet,
                Network.MainNet,
                platformContexts.ton,
                walletId,
                databaseKey = prepareDatabase(account.id, walletId),
                httpClient = httpClient,
            ),
            tonWallet,
            version,
        )
    }

    // Both wallet versions of an account share the account's database key.
    private suspend fun prepareDatabase(accountId: String, walletId: String): ByteArray {
        val databaseKey = tonKitDatabaseKeyProvider.awaitKey(accountId)
        TonKit.migrateDatabase(platformContexts.ton, Network.MainNet, walletId, databaseKey)
        return databaseKey
    }

    suspend fun clear(accountId: String) = lifecycleMutex.withLock {
        if (currentAccount?.id == accountId) {
            stop(entries.keys)
        }
        TonWalletVersion.entries.forEach { version ->
            val walletId = tonKitWalletId(accountId, version)
            Network.entries.forEach { TonKit.clear(platformContexts.ton, it, walletId) }
        }
        tonKitDatabaseKeyProvider.remove(accountId)
    }

    suspend fun unlink(account: Account, version: TonWalletVersion) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        val entry = entries[version] ?: return@withLock
        entry.useCount -= 1

        if (entry.useCount < 1) {
            stop(setOf(version))
        }
    }

    suspend fun startForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStarted {
            val account = currentAccount
            if (account == null || !isNetworkPaused(account)) {
                entries.values.forEach { entry ->
                    entry.wrapper.startNetwork()
                    entry.wrapper.tonKit.refresh()
                }
            }
        }
    }

    suspend fun stopForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStopped(backgroundManager) {
            entries.values.forEach { it.wrapper.stopNetwork() }
        }
    }

    suspend fun pauseNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        entries.values
            .map { it.wrapper }
            .filter { it.networkStarted }
            .forEach { it.stopNetwork() }
    }

    suspend fun resumeNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        entries.values
            .map { it.wrapper }
            .filterNot { it.networkStarted }
            .forEach { wrapper ->
                wrapper.startNetwork()
                wrapper.tonKit.refresh()
            }
    }

    suspend fun refresh() = lifecycleMutex.withLock {
        val account = currentAccount ?: return@withLock
        if (isNetworkPaused(account)) return@withLock
        entries.values.forEach { it.wrapper.tonKit.refresh() }
    }

    // Ownership is invalidated before the suspending teardown, so a cancellation here cannot leave a
    // half-stopped wrapper published. The teardown runs under NonCancellable and cancels the start
    // job first: a start still in flight would otherwise resurrect the SSE listener, which lives in
    // its own scope and outlives this job.
    private suspend fun stop(versions: Set<TonWalletVersion>) {
        val stopping = entries.filterKeys { it in versions }.values.toList()
        entries = entries - versions
        if (entries.isEmpty()) {
            httpClient = null
            currentAccount = null
        }
        withContext(NonCancellable) {
            stopping.forEach { entry ->
                entry.job.cancelAndJoin()
                entry.wrapper.tonKit.stop()
            }
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

    // Runs inside the entry's job and drives the kit it was created with: a later account switch cancels
    // this job, so the gate can never be evaluated for one account against another account's kit.
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

    private class Entry(val wrapper: TonKitWrapper, val job: Job) {
        var useCount = 0
    }
}

internal fun tonKitWalletId(accountId: String, version: TonWalletVersion): String =
    accountId + version.walletIdSuffix

/**
 * Persisted in kit database names and TON Connect sessions, so it must never change.
 * V4R2 keeps the bare account id: data created before W5 support stays in use.
 */
val TonWalletVersion.walletIdSuffix: String
    get() = when (this) {
        TonWalletVersion.V4R2 -> ""
        TonWalletVersion.W5 -> "-w5"
    }

class TonKitWrapper(val tonKit: TonKit, val tonWallet: TonWallet, val version: TonWalletVersion) {
    /** True once [TonKit.startListener] has been called and no matching [TonKit.stop] followed it. */
    var networkStarted: Boolean = false
}

fun TonKit.statusInfo() = buildMap {
    put("Sync State", syncStateFlow.value.toAdapterState())
    put("Event Sync State", eventSyncStateFlow.value.toAdapterState())
    put("Jetton Sync State", jettonSyncStateFlow.value.toAdapterState())
}

fun Jetton.tokenType(version: TonWalletVersion): TokenType =
    TonAsset.Jetton(address.toUserFriendly(true), version).tokenType

fun SyncState.toAdapterState(): AdapterState = when (this) {
    is SyncState.NotSynced -> AdapterState.NotSynced(error)
    is SyncState.Synced -> AdapterState.Synced
    is SyncState.Syncing -> AdapterState.Syncing()
}

fun Account.toTonWalletFullAccess(
    version: TonWalletVersion,
    hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    blockchainType: BlockchainType?,
): TonWallet.FullAccess {
    val toTonWallet = toTonWallet(version, hardwarePublicKeyStorage, blockchainType)

    return toTonWallet as? TonWallet.FullAccess ?: throw IllegalArgumentException("Watch Only")
}

fun Account.toTonWallet(
    version: TonWalletVersion,
    hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    blockchainType: BlockchainType?,
): TonWallet = when (this.type) {
    is AccountType.Mnemonic -> {
        val hdWallet = HDWallet(
            (this.type as AccountType.Mnemonic).seed,
            607,
            HDWallet.Purpose.BIP44,
            Curve.Ed25519
        )
        val privateKey = hdWallet.privateKey(0)
        TonWallet.Seed(privateKey.privKeyBytes.toFixedSize(32), version.kitVersion)
    }

    // isCompatibleWith keeps W5 tokens off watched addresses, so no W5 wallet reaches this.
    is AccountType.TonAddress -> when (version) {
        TonWalletVersion.V4R2 -> TonWallet.WatchOnly((this.type as AccountType.TonAddress).address)
        TonWalletVersion.W5 -> throw IllegalArgumentException("A watched TON address has no W5 wallet")
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
                get<TonHardwareSigners>(TonHardwareSigners::class.java).cardHashSigner(hardwarePublicKey),
                version.kitVersion,
            )
        }
    }

    else -> throw IllegalArgumentException(
        "Account type ${this.javaClass.simpleName} can not be converted to TonKit.WalletType"
    )
}

private val TonWalletVersion.kitVersion: KitTonWalletVersion
    get() = when (this) {
        TonWalletVersion.V4R2 -> KitTonWalletVersion.V4R2
        TonWalletVersion.W5 -> KitTonWalletVersion.W5
    }
