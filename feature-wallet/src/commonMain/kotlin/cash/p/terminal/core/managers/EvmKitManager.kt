package cash.p.terminal.core.managers

import cash.p.terminal.core.onPollingStarted
import cash.p.terminal.core.onPollingStopped
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.evmExplorerTransactionHash
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.RawTransactionBroadcastResult
import io.horizontalsystems.ethereumkit.models.Signature
import io.horizontalsystems.ethereumkit.models.SignedRawTransaction
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.merkleiokit.MerkleTransactionAdapter
import io.horizontalsystems.oneinchkit.OneInchKit
import io.horizontalsystems.uniswapkit.TokenFactory.UnsupportedChainError
import io.horizontalsystems.uniswapkit.UniswapKit
import io.horizontalsystems.uniswapkit.UniswapV3Kit
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.atomic.AtomicInteger

class EvmKitManager(
    val chain: Chain,
    private val backgroundManager: IBackgroundManager,
    private val syncSourceManager: EvmSyncSourceManager,
    private val backgroundKeepAliveManager: BackgroundKeepAliveManager,
    private val networkErrorTracker: NetworkErrorTracker,
    private val offlineModeManager: OfflineModeManager,
    private val databaseKeys: EvmKitDatabaseKeyProvider,
    private val platformContexts: KitPlatformContexts,
) {
    private val evmSignerFactory: EvmSignerFactory
            by inject(EvmSignerFactory::class.java)
    private val hardwareSigners: EvmHardwareSigners
            by inject(EvmHardwareSigners::class.java)

    private val lifecycleMutex = Mutex()
    private val pollingSessionCount = AtomicInteger(0)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    init {
        coroutineScope.launch {
            syncSourceManager.syncSourceObservable.asFlow().collect { blockchain ->
                handleUpdateNetwork(blockchain)
            }
        }
    }

    private fun handleUpdateNetwork(blockchainType: BlockchainType) {
        if (blockchainType != evmKitWrapper?.blockchainType) return

        stopEvmKit()

        evmKitUpdatedSubject.onNext(Unit)
    }

    private val kitStartedSubject = BehaviorSubject.createDefault(false)
    val kitStartedObservable: Observable<Boolean> = kitStartedSubject

    var evmKitWrapper: EvmKitWrapper? = null
        private set(value) {
            field = value

            kitStartedSubject.onNext(value != null)
        }

    private var useCount = AtomicInteger(0)
    var currentAccount: Account? = null
        private set
    private val evmKitUpdatedSubject = PublishSubject.create<Unit>()

    val evmKitUpdatedObservable: Observable<Unit>
        get() = evmKitUpdatedSubject

    val statusInfo: Map<String, Any>?
        get() = evmKitWrapper?.let { wrapper ->
            networkErrorTracker.mergedStatusInfo(
                wrapper.evmKit.statusInfo(), wrapper.blockchainType, currentAccount?.id
            )
        }

    suspend fun getEvmKitWrapper(
        account: Account,
        blockchainType: BlockchainType
    ): EvmKitWrapper = lifecycleMutex.withLock {
        if (evmKitWrapper != null && currentAccount != account) {
            stopEvmKit()
        }

        if (this.evmKitWrapper == null) {
            evmKitWrapper = createKitInstance(
                account = account,
                blockchainType = blockchainType
            )
            useCount.set(0)
            currentAccount = account
            subscribeToEvents()
        }
        useCount.incrementAndGet()
        requireNotNull(this.evmKitWrapper)
    }

    private suspend fun createKitInstance(
        account: Account,
        blockchainType: BlockchainType
    ): EvmKitWrapper {
        val syncSource = syncSourceManager.getSyncSource(blockchainType)

        val address = evmSignerFactory.resolveAddress(account, blockchainType, chain)
            ?: throw UnsupportedAccountException()
        val signer = evmSignerFactory.createSigner(account, blockchainType, chain)

        val databaseKey = databaseKeys.awaitKey(account.id)
        migrateDatabases(account.id, databaseKey)

        val eventListenerFactory =
            NetworkErrorEventListener.Factory(blockchainType, account.id, networkErrorTracker, recordHttpErrors = true)

        val evmKit = EthereumKit.getInstance(
            application = platformContexts.ethereum,
            address = address,
            chain = chain,
            rpcSource = syncSource.rpcSource,
            transactionSource = syncSource.transactionSource,
            walletId = account.id,
            databaseKey = databaseKey,
            scanHistoricalEip20 = account.origin == AccountOrigin.Restored,
            eventListenerFactory = eventListenerFactory
        )

        addDecorators(evmKit)

        val merkleTransactionAdapter = try {
            MerkleTransactionAdapter.getInstance(
                merkleIoPubKey = SharedAppConfig.merkleIoKey,
                address = address,
                chain = chain,
                context = platformContexts.ethereum,
                walletId = account.id,
                databaseKey = databaseKey,
                transactionManager = evmKit.transactionManager,
                sourceTag = "pcash-wallet-android",
                transactionSyncSourceStorage = evmKit.transactionSyncSourceStorage,
                eventListenerFactory = eventListenerFactory
            )
        } catch (error: Throwable) {
            // Not in evmKitWrapper yet, so neither unlink() nor stopFor() would ever stop it.
            evmKit.stop()
            throw error
        }
        merkleTransactionAdapter?.registerInKit(evmKit)

        if (!offlineModeManager.isNetworkPaused(account.id, blockchainType)) {
            evmKit.start()
        }

        return EvmKitWrapper(
            evmKit = evmKit,
            blockchainType = blockchainType,
            signer = signer,
            merkleTransactionAdapter = merkleTransactionAdapter,
            databaseKey = databaseKey,
            hardwareSigners = hardwareSigners,
        )
    }

    private fun addDecorators(evmKit: EthereumKit) {
        Erc20Kit.addTransactionSyncer(evmKit)
        Erc20Kit.addDecorators(evmKit)

        UniswapKit.addDecorators(evmKit)
        try {
            UniswapV3Kit.addDecorators(evmKit)
        } catch (e: UnsupportedChainError.NoWethAddress) {
            //do nothing
        }
        OneInchKit.addDecorators(evmKit)
    }

    /** The kit refuses plaintext files, so every database must be encrypted before it opens. */
    private suspend fun migrateDatabases(walletId: String, databaseKey: ByteArray) {
        val context = platformContexts.ethereum
        EthereumKit.migrateDatabase(context, chain, walletId, databaseKey)
        Erc20Kit.migrateDatabases(context, chain, walletId, databaseKey)
        MerkleTransactionAdapter.migrateDatabase(context, chain, walletId, databaseKey)
    }

    suspend fun stopFor(accountId: String) = lifecycleMutex.withLock {
        if (currentAccount?.id == accountId) {
            stopEvmKit()
        }
    }

    suspend fun unlink(wrapper: EvmKitWrapper) = lifecycleMutex.withLock {
        // A release owed to an already-stopped instance must not count against its replacement.
        if (wrapper !== evmKitWrapper) return@withLock

        useCount.decrementAndGet()

        if (useCount.get() < 1) {
            stopEvmKit()
        }
    }

    suspend fun startForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStarted {
            val wrapper = evmKitWrapper
            val account = currentAccount
            if (wrapper != null &&
                (account == null || !offlineModeManager.isNetworkPaused(account.id, wrapper.blockchainType))
            ) {
                wrapper.evmKit.start()
                wrapper.evmKit.refresh()
            }
        }
    }

    suspend fun stopForPolling() = lifecycleMutex.withLock {
        pollingSessionCount.onPollingStopped(backgroundManager) {
            evmKitWrapper?.evmKit?.stop()
        }
    }

    suspend fun pauseNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        evmKitWrapper?.evmKit?.pauseNetwork()
    }

    suspend fun resumeNetwork(account: Account) = lifecycleMutex.withLock {
        if (account != currentAccount) return@withLock
        evmKitWrapper?.evmKit?.let { kit ->
            kit.start()
            kit.refresh()
        }
    }

    private fun subscribeToEvents() {
        job = coroutineScope.launch {
            // Latest: a return to the background cancels a restart still waiting out its delay.
            backgroundManager.stateFlow.collectLatest { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    evmKitWrapper?.let { wrapper ->
                        delay(FOREGROUND_RESTART_DELAY_MS)
                        val account = currentAccount
                        if (account != null &&
                            offlineModeManager.isNetworkPaused(account.id, wrapper.blockchainType)
                        ) {
                            wrapper.evmKit.attachLocalState()
                        } else {
                            wrapper.evmKit.start()
                            wrapper.evmKit.refresh()
                        }
                    }
                } else if (state == BackgroundManagerState.EnterBackground) {
                    val wrapper = evmKitWrapper ?: return@collectLatest
                    if (pollingSessionCount.get() == 0 && !backgroundKeepAliveManager.isKeepAlive(
                            wrapper.blockchainType
                        )
                    ) {
                        wrapper.evmKit.stop()
                    } else {
                        txPollerLogger.d { "EvmKit(${wrapper.blockchainType.uid}) staying alive" }
                    }
                }
            }
        }
    }

    private fun stopEvmKit() {
        job?.cancel()
        evmKitWrapper?.evmKit?.stop()
        evmKitWrapper = null
        currentAccount = null
    }

    fun refresh() {
        evmKitWrapper?.evmKit?.refresh()
    }

    private companion object {
        const val FOREGROUND_RESTART_DELAY_MS = 1000L
        val txPollerLogger = Logger.withTag("TxPoller")
    }
}

class EvmKitWrapper(
    val evmKit: EthereumKit,
    val blockchainType: BlockchainType,
    val signer: Signer?,
    val merkleTransactionAdapter: MerkleTransactionAdapter?,
    val databaseKey: ByteArray,
    private val hardwareSigners: EvmHardwareSigners,
) {

    /** Signs without sending, so callers can record the final hash before the network call. */
    suspend fun prepare(
        transactionData: TransactionData,
        gasPrice: GasPrice,
        gasLimit: Long,
        nonce: Long?,
        mevProtectionEnabled: Boolean = false,
    ): PreparedEvmTransaction {
        if (mevProtectionEnabled && merkleTransactionAdapter == null) {
            throw IllegalStateException("MEV Protection is enabled, but MerkleTransactionAdapter is not initialized")
        }

        val rawTransaction =
            evmKit.rawTransaction(transactionData, gasPrice, gasLimit, nonce).await()
        val (reconciledRawTransaction, signature) = signReconciled(rawTransaction)
        return PreparedEvmTransaction(
            rawTransaction = reconciledRawTransaction,
            signature = signature,
            signedRaw = evmKit.signedRawTransaction(reconciledRawTransaction, signature),
            mevProtected = mevProtectionEnabled,
        )
    }

    suspend fun broadcast(prepared: PreparedEvmTransaction): FullTransaction {
        val adapter = merkleTransactionAdapter
        return if (prepared.mevProtected && adapter != null) {
            adapter.send(prepared.rawTransaction, prepared.signature).await()
        } else {
            evmKit.send(prepared.rawTransaction, prepared.signature).await()
        }
    }

    suspend fun signedRawTransaction(
        transactionData: TransactionData,
        gasPrice: GasPrice,
        gasLimit: Long,
        nonce: Long?,
    ): SignedRawTransaction = prepare(transactionData, gasPrice, gasLimit, nonce).signedRaw

    suspend fun broadcastRawTransaction(rawTransactionHex: String): RawTransactionBroadcastResult =
        evmKit.broadcastRawTransaction(rawTransactionHex).await()

    /**
     * Signs [rawTransaction] and returns the transaction that was actually signed together with its
     * signature. Hardware wallets return the device-signed transaction, reconciled from the fields the
     * device signed, so the returned raw transaction can differ from the input. Always go through this
     * instead of the base Signer.signedTransaction(), which signs hardware-wallet transactions with a
     * mock key.
     */
    suspend fun signReconciled(rawTransaction: RawTransaction): Pair<RawTransaction, Signature> {
        val signer = signer ?: throw EvmSignerNotInitializedException()
        return hardwareSigners.signReconciled(signer, rawTransaction)
            ?: (rawTransaction to signer.signature(rawTransaction))
    }

}

/** Not a data class on purpose: a generated toString() would print the signature. */
class PreparedEvmTransaction(
    val rawTransaction: RawTransaction,
    val signature: Signature,
    val signedRaw: SignedRawTransaction,
    val mevProtected: Boolean,
) {
    val hash: String get() = signedRaw.hash.toRawHexString().evmExplorerTransactionHash()
}

internal class EvmSignerNotInitializedException : IllegalStateException("Signer is not initialized for this EVM kit")
