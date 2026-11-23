package cash.p.terminal.core.managers

import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.core.storage.liveAccountIds
import cash.p.terminal.wallet.IAccountManager
import co.touchlab.kermit.Logger
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppEventSuccessEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.SignTransaction
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The only owner of TON Connect kits: a kit's database is open exactly while an operation or listener on it can
 * run, and closes once when its scope leaves the session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TonConnectManager(
    val adapterFactory: AdapterFactory,
    private val appName: String,
    private val appVersion: String,
    private val storage: TonConnectStorage,
    private val accountManager: IAccountManager,
    private val router: StorageRouter,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logger = Logger.withTag("TonConnectManager")

    // Guards `entries`; a lease is taken and a kit is opened or closed only under it.
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, KitEntry>()
    private val openEntries = MutableStateFlow<Map<String, KitEntry>>(emptyMap())
    private val reconcileSignals = Channel<Unit>(Channel.CONFLATED)
    private val started = AtomicBoolean(false)

    private val tonApi = TonKit.getTonApi(Network.MainNet, SharedAppConfig.tonApiKeys)
    val transactionSigner = TonKit.getTransactionSigner(tonApi)

    val openScopes: StateFlow<Set<String>> = openEntries
        .map { it.keys }
        .stateIn(dispatcherProvider.applicationScope, SharingStarted.Eagerly, emptySet())

    // A narrowed-away scope stays open until its release reaches it; its sessions are not listed meanwhile.
    val dApps: Flow<List<DAppEntity>> = openEntries.flatMapLatest { entries ->
        val live = entries.filterKeys(::isLive)
        if (live.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(live.values.map { it.dApps }) { it.flatMap { list -> list } }
        }
    }

    private val _pendingSendRequest = MutableStateFlow<SignTransaction?>(null)
    val pendingSendRequest: StateFlow<SignTransaction?> = _pendingSendRequest.asStateFlow()

    // Separate from the consumable pending state: navigation may take a request before an observer sees it.
    // Visibility is checked on delivery: a level may narrow while the arrival waits in the buffer.
    private val _sendRequestArrivals = MutableSharedFlow<SignTransaction>(extraBufferCapacity = ARRIVALS_BUFFER)
    val sendRequestArrivals: Flow<SignTransaction> = _sendRequestArrivals.filter(::isVisible)

    // Queued until the root effect collects it: a cold-start deep link is handled before that effect starts.
    private val dappRequests = Channel<DAppRequest>(Channel.BUFFERED)
    val dappRequestFlow: Flow<DAppRequest> = dappRequests.receiveAsFlow()

    /** Starts following the accounts; a later call retries opening the kits whose earlier start failed. */
    fun start() {
        reconcileSignals.trySend(Unit)
        if (!started.compareAndSet(false, true)) return
        val scope = dispatcherProvider.applicationScope
        // Subscribed before the first pass reads the accounts, so no change between them is missed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            accountManager.accountsFlow.collect { reconcileSignals.trySend(Unit) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            accountManager.accountsDeletedFlowable.asFlow().collect { reconcileSignals.trySend(Unit) }
        }
        scope.launch {
            reconcileSignals.consumeEach { logFailure("reconcile TON Connect kits") { reconcile() } }
        }
    }

    private suspend fun reconcile() {
        val live = storage.liveScopes()
        mutex.withLock {
            (entries.keys - live).forEach { closeKit(it) }
            live.filter { it !in entries && storage.isCreated(it) }.forEach { scope ->
                logFailure("open TON Connect kit") { openKit(scope) }
            }
        }
        if (!storage.sharedByAccounts) return
        val walletIds = liveWalletIds()
        openEntries.value.keys.forEach { scope -> onKit(scope) { removeDAppsExcept(walletIds) } }
    }

    /** Closes the kits of every account not in [kept] now, without waiting for the next reconcile pass. */
    suspend fun retainAccounts(kept: Set<String>) {
        val keptScopes = kept.mapTo(mutableSetOf(), storage::scopeOf)
        mutex.withLock { (entries.keys - keptScopes).forEach { closeKit(it) } }
    }

    /** After a keystore reset: the next open starts over with a new key and an empty database. */
    fun reset() {
        storage.liveScopes().forEach(storage::removeDatabaseKey)
        dispatcherProvider.applicationScope.launch {
            logFailure("drop TON Connect kits after reset") {
                mutex.withLock {
                    val scopes = entries.keys + storage.liveScopes()
                    entries.keys.toList().forEach { closeKit(it) }
                    // An open in flight may have persisted a key wrapped by the old master key meanwhile.
                    scopes.forEach(storage::removeDatabaseKey)
                }
            }
        }
    }

    suspend fun getManifest(accountId: String, url: String): DAppManifestEntity =
        openingLease(storage.scopeOf(accountId)).use { getManifest(url) }

    suspend fun connect(
        accountId: String,
        request: DAppRequestEntity,
        manifest: DAppManifestEntity,
        walletId: String,
        tonWallet: TonWallet.FullAccess,
    ): DAppEventSuccessEntity =
        openingLease(storage.scopeOf(accountId)).use { connect(request, manifest, walletId, tonWallet) }

    /** False when the dApp's kit is closed: its session went with the account's level or the account. */
    suspend fun disconnect(dApp: DAppEntity): Boolean = onKit(scopeOfWallet(dApp.walletId)) { disconnect(dApp) } != null

    suspend fun approve(request: SendRequestEntity, boc: String): Boolean =
        onKit(scopeOf(request)) { approve(request, boc) } != null

    suspend fun reject(request: SendRequestEntity): Boolean = onKit(scopeOf(request)) { reject(request) } != null

    suspend fun badRequest(request: SendRequestEntity): Boolean =
        onKit(scopeOf(request)) { badRequest(request) } != null

    fun isOpen(request: SendRequestEntity): Boolean = scopeOf(request).let { it in openEntries.value && isLive(it) }

    private fun scopeOf(request: SendRequestEntity): String = scopeOfWallet(request.dAppId.substringBefore(":"))

    /** The pending request, taken only when its account is visible now; another level's request stays hidden. */
    fun takeShowableSendRequest(): SignTransaction? {
        val request = _pendingSendRequest.value?.takeIf(::isVisible) ?: return null
        return request.takeIf { _pendingSendRequest.compareAndSet(it, null) }
    }

    private fun isVisible(request: SignTransaction): Boolean =
        accountManager.account(TonConnectWalletId.parse(request.dApp.walletId).accountId) != null

    /** Chain time decides whether a message is accepted; null when it cannot be fetched. */
    suspend fun networkTimeSeconds(): Long? = try {
        tonApi.getRawTime().toLong()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Failed to fetch TON network time" }
        null
    }

    suspend fun handle(scannedText: String, closeAppOnResult: Boolean = false) {
        try {
            TonConnectKit.readData(scannedText)
            dappRequests.send(DAppRequest(scannedText, closeAppOnResult))
        } catch (e: Throwable) {
            // The parser's message embeds the untrusted payload.
            logger.w { "Invalid TON Connect request: ${e::class.simpleName}" }
        }
    }

    /** The `tc://` branch of a pasted connection link; false when [text] is not one. */
    fun openConnectionLink(text: String, scope: CoroutineScope): Boolean {
        if (!text.startsWith("tc://")) return false
        scope.launch { handle(text) }
        return true
    }

    private fun scopeOfWallet(walletId: String) = storage.scopeOf(TonConnectWalletId.parse(walletId).accountId)

    private suspend fun <T> onKit(scope: String, operation: suspend TonConnectKit.() -> T): T? =
        lease(scope, create = false)?.use(operation)

    private suspend fun openingLease(scope: String): Lease = checkNotNull(lease(scope, create = true))

    // A scope that left the accounts may still be open until its release reaches it: it is answered no more.
    private fun isLive(scope: String) = scope in storage.liveScopes()

    private suspend fun lease(scope: String, create: Boolean): Lease? = mutex.withLock {
        if (!create && !isLive(scope)) return null
        val entry = entries[scope] ?: if (create) openKit(scope) else return null
        Lease(entry.kit, Job(entry.operations))
    }

    // Caller-bound: cancelling the caller cancels the operation, and the lease still ends only after it has.
    private suspend fun <T> Lease.use(operation: suspend TonConnectKit.() -> T): T = try {
        kit.operation()
    } finally {
        job.complete()
    }

    private suspend fun openKit(scope: String): KitEntry {
        val kit = createKit(scope)
        val listeners = SupervisorJob()
        val listenerScope = CoroutineScope(dispatcherProvider.applicationScope.coroutineContext + listeners)
        listenerScope.launch(start = CoroutineStart.UNDISPATCHED) { kit.sendRequestFlow.collect(::onSendRequest) }
        val dApps = kit.getDApps().stateIn(listenerScope, SharingStarted.Eagerly, emptyList())
        val entry = KitEntry(kit, listeners, SupervisorJob(), dApps)
        entries[scope] = entry
        openEntries.update { it + (scope to entry) }
        kit.start()
        return entry
    }

    private fun onSendRequest(request: SignTransaction) {
        _pendingSendRequest.value = request
        _sendRequestArrivals.tryEmit(request)
    }

    // Under the mutex, so no lease can start; nothing a caller cancels can leave the database open untracked.
    private suspend fun closeKit(scope: String) = withContext(NonCancellable) {
        val entry = entries[scope] ?: return@withContext
        openEntries.update { it - scope }
        entry.listeners.cancelAndJoin()
        entry.operations.children.toList().joinAll()
        logFailure("close TON Connect kit") { entry.kit.close() }
        _pendingSendRequest.update { pending -> pending?.takeUnless { scopeOfWallet(it.dApp.walletId) == scope } }
        entries.remove(scope)
    }

    private suspend fun createKit(scope: String): TonConnectKit {
        val databaseKey = storage.databaseKey(scope)
        val context = storage.platformContext(scope)
        val nameResolver = storage.nameResolver(scope)
        // A cancelled caller must not leave a fresh key stored over a database that was never recreated.
        withContext(NonCancellable) { prepareDatabase(scope, databaseKey) }
        val kit = TonConnectKit.getInstance(context, databaseKey.bytes, appName, appVersion, nameResolver)
        try {
            // Sessions of accounts deleted before this version would otherwise stay subscribed.
            if (storage.sharedByAccounts) kit.removeDAppsExcept(liveWalletIds())
        } catch (e: Throwable) {
            withContext(NonCancellable) { kit.close() }
            throw e
        }
        return kit
    }

    private suspend fun prepareDatabase(scope: String, databaseKey: DatabaseKey) {
        val context = storage.platformContext(scope)
        val nameResolver = storage.nameResolver(scope)
        try {
            TonConnectKit.migrateDatabase(context, databaseKey.bytes, nameResolver)
        } catch (e: DatabaseKeyMismatchException) {
            // A stored key may fail to open transiently; only a just-created key proves the old database is lost.
            if (!databaseKey.isNew) throw e
            try {
                TonConnectKit.clear(context, nameResolver)
                TonConnectKit.migrateDatabase(context, databaseKey.bytes, nameResolver)
            } catch (recreation: Throwable) {
                // The fresh key protects nothing yet; dropping it lets the next open discard the database again.
                storage.removeDatabaseKey(scope)
                throw recreation
            }
        }
    }

    private suspend fun liveWalletIds(): List<String> = withContext(dispatcherProvider.io) {
        router.liveAccountIds().flatMap(TonConnectWalletId::allOf)
    }

    private suspend fun logFailure(action: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to $action" }
        }
    }

    private class KitEntry(
        val kit: TonConnectKit,
        val listeners: Job,
        val operations: CompletableJob,
        val dApps: StateFlow<List<DAppEntity>>,
    )

    private class Lease(val kit: TonConnectKit, val job: CompletableJob)

    private companion object {
        const val ARRIVALS_BUFFER = 16
    }
}

data class DAppRequest(
    val uri: String,
    val closeAppOnResult: Boolean
)
