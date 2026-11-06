package cash.p.terminal.core.adapters

import cash.p.terminal.core.BitcoinSwapSendResult
import cash.p.terminal.core.IFeeRateProvider
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.OfflineBitcoinSignRequest
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineBitcoinTransaction
import cash.p.terminal.core.onPollingStarted
import cash.p.terminal.core.onPollingStopped
import cash.p.terminal.core.UnsupportedFilterException
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.OfflineTransactionOutpoint
import cash.p.terminal.entities.TransactionDataSortMode
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.TransactionLockInfo
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.send_trezor_time_lock_not_supported
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import co.touchlab.kermit.Logger
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.appendNetworkErrors
import cash.p.terminal.core.managers.toNetworkErrorInfo
import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.network.BitcoinNetworkErrorListener
import io.horizontalsystems.bitcoincore.core.IPluginData
import io.horizontalsystems.bitcoincore.models.RawTransactionBroadcastStatus
import io.horizontalsystems.bitcoincore.models.TransactionDataSortType
import io.horizontalsystems.bitcoincore.models.TransactionFilterType
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.horizontalsystems.bitcoincore.models.TransactionStatus
import io.horizontalsystems.bitcoincore.models.TransactionType
import io.horizontalsystems.bitcoincore.rbf.ReplacementTransaction
import io.horizontalsystems.bitcoincore.rbf.ReplacementTransactionInfo
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.bitcoincore.storage.UnspentOutputInfo
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.hodler.HodlerOutputData
import io.horizontalsystems.hodler.HodlerPlugin
import cash.p.terminal.core.toRawHexString
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx2.await
import org.koin.java.KoinJavaComponent.get
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

abstract class BitcoinBaseAdapter(
    open val kit: AbstractKit,
    open val syncMode: BitcoinCore.SyncMode,
    private val backgroundManager: IBackgroundManager,
    val wallet: Wallet,
    private val displayConfirmationsThreshold: Int,
    protected val decimal: Int = 8,
    protected val feeRateProvider: IFeeRateProvider? = null
) : IAdapter, ITransactionsAdapter, IBalanceAdapter, IReceiveAdapter, ISendBitcoinAdapter,
    OfflineTransactionAdapter<SignedOfflineBitcoinTransaction> {

    private val backgroundKeepAliveManager: BackgroundKeepAliveManager
            by inject(BackgroundKeepAliveManager::class.java)

    private val networkErrorTracker: NetworkErrorTracker
            by inject(NetworkErrorTracker::class.java)

    private val offlineModeManager: OfflineModeManager
            by inject(OfflineModeManager::class.java)

    private val replacementRegistry: BitcoinReplacementRegistry
            by inject(BitcoinReplacementRegistry::class.java)

    private val pollingSessionCount = AtomicInteger(0)

    protected val scope = CoroutineScope(Dispatchers.Default)
    private var feeJob: Job? = null

    // Fee state
    private val _fee = MutableStateFlow(BigDecimal.ZERO)
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()
    private var recommendedFeeRate: Int? = null

    override val maxSpendableBalance: BigDecimal
        get() {
            val feeRate = recommendedFeeRate ?: return balanceData.available
            return availableBalance(
                feeRate = feeRate,
                address = null,
                memo = null,
                unspentOutputs = null,
                pluginData = null,
                changeToFirstInput = false,
                utxoFilters = UtxoFilters()
            )
        }

    protected suspend fun estimateFeeForMax() {
        tryOrNull {
            val feeRates = feeRateProvider?.getFeeRates() ?: return@tryOrNull
            recommendedFeeRate = feeRates.recommended

            val maxSpendable = maxSpendableBalance
            val totalBalance = balanceData.available
            _fee.value = maxOf(totalBalance - maxSpendable, BigDecimal.ZERO)
        }
    }

    //
    // Adapter implementation
    //

    private var syncState: AdapterState = AdapterState.Connecting
        set(value) {
            if (value != field) {
                field = value
                adapterStateUpdatedSubject.onNext(Unit)
            }
        }

    private var lastSyncProgress: Int = 0

    override val transactionsState
        get() = syncState

    override val balanceState
        get() = syncState

    override val lastBlockInfo: LastBlockInfo?
        get() = kit.lastBlockInfo?.let { LastBlockInfo(it.height, it.timestamp) }

    override val receiveAddress: String
        get() = kit.receiveAddress()

    override val isMainNet: Boolean = true

    override val isAddressHistorySupported: Boolean = true

    protected val balanceUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()
    protected val lastBlockUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()
    protected val adapterStateUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()
    protected val transactionRecordsSubject: PublishSubject<List<TransactionRecord>> =
        PublishSubject.create()
    private val transactionsReloadSubject: PublishSubject<Unit> = PublishSubject.create()

    final override val unspentOutputs: List<UnspentOutputInfo>
        get() = kit.getUnspentOutputs(UtxoFilters())

    override val balanceUpdatedFlow: Flow<Unit>
        get() = balanceUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()

    override val lastBlockUpdatedFlowable: Flowable<Unit>
        get() = lastBlockUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER)

    override val transactionsStateUpdatedFlowable: Flowable<Unit>
        get() = adapterStateUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER)

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = adapterStateUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()

    override fun getTransactionsReloadSignalFlow(): Flow<Unit> = merge(
        transactionsReloadSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow(),
        replacementRegistry.changes,
    )

    // Shared handler for the BitcoinCore.Listener onTransactionsDelete callback (e.g. a stuck
    // NEW transaction expiring). Each concrete adapter's Kit.Listener interface requires its own
    // override, so subclasses delegate here to keep the logic in one place.
    protected fun onTransactionsDeleted() {
        transactionsReloadSubject.onNext(Unit)
        balanceUpdatedSubject.onNext(Unit)
    }

    override fun getTransactionRecordsFlow(
        token: Token?,
        transactionType: FilterTransactionType,
        address: String?,
    ): Flow<List<TransactionRecord>> = when (address) {
        null -> getTransactionRecordsFlowable(transactionType).asFlow()
        else -> emptyFlow()
    }

    private fun getTransactionRecordsFlowable(
        transactionType: FilterTransactionType
    ): Flowable<List<TransactionRecord>> {
        val observable: Observable<List<TransactionRecord>> = when (transactionType) {
            FilterTransactionType.All -> {
                transactionRecordsSubject
            }

            FilterTransactionType.Incoming,
            FilterTransactionType.Outgoing -> {
                transactionRecordsSubject
                    .map { records -> filterBitcoinTransactionRecords(records, transactionType) }
                    .filter { it.isNotEmpty() }
            }

            FilterTransactionType.Swap,
            FilterTransactionType.Approve -> {
                Observable.empty()
            }
        }

        return observable.toFlowable(BackpressureStrategy.BUFFER)
    }

    protected fun filterBitcoinTransactionRecords(
        records: List<TransactionRecord>,
        transactionType: FilterTransactionType
    ): List<TransactionRecord> {
        return when (transactionType) {
            FilterTransactionType.All -> records
            FilterTransactionType.Incoming -> {
                records.filter {
                    it.transactionRecordType == TransactionRecordType.BITCOIN_INCOMING ||
                            (it.transactionRecordType == TransactionRecordType.BITCOIN_OUTGOING &&
                                    (it as BitcoinTransactionRecord).sentToSelf)
                }
            }

            FilterTransactionType.Outgoing -> {
                records.filter { it.transactionRecordType == TransactionRecordType.BITCOIN_OUTGOING }
            }

            FilterTransactionType.Swap,
            FilterTransactionType.Approve -> {
                emptyList()
            }
        }
    }

    override val debugInfo: String = ""

    override val balanceData: BalanceData
        get() = BalanceData(balance, balanceTimeLocked, balanceNotRelayed)

    private val balance: BigDecimal
        get() = satoshiToBTC(kit.balance.spendable)

    private val balanceTimeLocked: BigDecimal
        get() = satoshiToBTC(kit.balance.unspendableTimeLocked)

    private val balanceNotRelayed: BigDecimal
        get() = satoshiToBTC(kit.balance.unspendableNotRelayed)

    override fun attachLocalData() {
        // Installed here (not in an init block): `kit` is an overridden open val whose
        // subclass backing field is still null during base-class construction, so touching
        // it from init would NPE. By this point the kit is fully constructed, and network
        // errors can only occur once syncing has begun.
        kit.networkErrorListener = BitcoinNetworkErrorListener { error ->
            networkErrorTracker.record(
                wallet.token.blockchainType,
                wallet.account.id,
                error.toNetworkErrorInfo()
            )
        }
        subscribeToEvents()
    }

    override fun resumeNetwork() {
        kit.start()
        feeJob = scope.launch {
            estimateFeeForMax()
        }
    }

    override fun pauseNetwork() {
        kit.pauseNetwork()
        feeJob?.cancel()
    }

    override fun stop() {
        kit.dispose()
        scope.cancel()
    }

    override suspend fun refresh() {
        kit.refresh()
    }

    fun startForPolling() {
        pollingSessionCount.onPollingStarted {
            if (!offlineModeManager.isNetworkPaused(wallet.account.id, wallet.token.blockchainType)) {
                kit.onEnterForeground()
            }
        }
    }

    fun stopForPolling() {
        pollingSessionCount.onPollingStopped(backgroundManager) {
            kit.onEnterBackground()
        }
    }

    override suspend fun getTransactions(
        from: TransactionRecord?,
        token: Token?,
        limit: Int,
        transactionType: FilterTransactionType,
        address: String?,
    ) = when (address) {
        null -> getTransactionsAsync(from, limit, transactionType)
        else -> emptyList<TransactionRecord>()
    }

    // Records superseded by a replacement are hidden, so a page is refilled until it is full
    // or the kit runs out: a short page tells the caller that everything is loaded.
    private suspend fun getTransactionsAsync(
        from: TransactionRecord?,
        limit: Int,
        transactionType: FilterTransactionType
    ): List<TransactionRecord> {
        val filter = try {
            getBitcoinTransactionTypeFilter(transactionType)
        } catch (e: UnsupportedFilterException) {
            return emptyList()
        }
        val visible = mutableListOf<TransactionRecord>()
        var cursorUid = from?.uid
        do {
            val count = limit - visible.size
            val page = kit.transactions(cursorUid, filter, count).map { it.map(::transactionRecord) }.await()
            visible += page.filter { it.replacedByHash == null }
            cursorUid = page.lastOrNull()?.uid
        } while (visible.size < limit && page.size == count)
        return visible
    }

    private fun getBitcoinTransactionTypeFilter(transactionType: FilterTransactionType): TransactionFilterType? {
        return when (transactionType) {
            FilterTransactionType.All -> null
            FilterTransactionType.Incoming -> TransactionFilterType.Incoming
            FilterTransactionType.Outgoing -> TransactionFilterType.Outgoing
            else -> throw UnsupportedFilterException()
        }
    }

    private fun subscribeToEvents() {
        scope.launch {
            backgroundManager.stateFlow.collect { state ->
                when (state) {
                    BackgroundManagerState.EnterForeground -> {
                        kit.onEnterForeground()
                    }

                    BackgroundManagerState.EnterBackground -> {
                        if (pollingSessionCount.get() == 0 &&
                            !backgroundKeepAliveManager.isKeepAlive(wallet.token.blockchainType)
                        ) {
                            kit.onEnterBackground()
                        } else {
                            Logger.withTag("TxPoller")
                                .d { "BitcoinKit(${wallet.token.blockchainType.uid}) staying alive" }
                        }
                    }

                    BackgroundManagerState.Unknown,
                    BackgroundManagerState.AllActivitiesDestroyed -> {

                    }
                }
            }
        }
    }

    override fun getRawTransaction(transactionHash: String): String? {
        return kit.getRawTransaction(transactionHash)
    }

    fun speedUpTransactionInfo(transactionHash: String): ReplacementTransactionInfo? {
        return kit.speedUpTransactionInfo(transactionHash)
    }

    fun cancelTransactionInfo(transactionHash: String): ReplacementTransactionInfo? {
        return kit.cancelTransactionInfo(transactionHash)
    }

    fun speedUpTransaction(
        transactionHash: String,
        minFee: Long
    ): Pair<ReplacementTransaction, BitcoinTransactionRecord> {
        val replacement = kit.speedUpTransaction(transactionHash, minFee)
        return Pair(replacement, transactionRecord(replacement.info))
    }

    fun cancelTransaction(
        transactionHash: String,
        minFee: Long
    ): Pair<ReplacementTransaction, BitcoinTransactionRecord> {
        val replacement = kit.cancelTransaction(transactionHash, minFee)
        return Pair(replacement, transactionRecord(replacement.info))
    }

    suspend fun send(replacementTransaction: ReplacementTransaction): FullTransaction {
        return kit.send(replacementTransaction)
    }

    protected fun setState(kitState: BitcoinCore.KitState) {
        syncState = when (kitState) {
            is BitcoinCore.KitState.Synced -> {
                lastSyncProgress = 100
                AdapterState.Synced
            }

            is BitcoinCore.KitState.NotSynced -> {
                AdapterState.NotSynced(kitState.exception)
            }

            is BitcoinCore.KitState.ApiSyncing -> {
                AdapterState.SearchingTxs(kitState.transactions)
            }

            is BitcoinCore.KitState.Syncing -> {
                val progress = (kitState.progress * 100).toInt()
                lastSyncProgress = progress

                val substatus = when (val sub = kitState.substatus) {
                    is BitcoinCore.SyncSubstatus.WaitingForPeers ->
                        AdapterState.Substatus.WaitingForPeers(sub.connected, sub.required)

                    null -> null
                }

                val lastBlockDate = kit.lastBlockInfo?.timestamp?.let { Date(it * 1000) }

                val currentHeight = kit.lastBlockInfo?.height
                val maxBlockHeight = kitState.maxBlockHeight
                val blocksRemained = if (currentHeight != null && maxBlockHeight != null) {
                    (maxBlockHeight.toLong() - currentHeight.toLong()).coerceAtLeast(0)
                } else {
                    null
                }

                if (progress >= 100) {
                    AdapterState.Syncing(
                        progress = 100.0,
                        lastBlockDate = lastBlockDate,
                        blocksRemained = null,
                        substatus = substatus
                    )
                } else {
                    AdapterState.Syncing(
                        progress = progress.toDouble(),
                        lastBlockDate = lastBlockDate,
                        blocksRemained = blocksRemained,
                        substatus = substatus
                    )
                }
            }
        }
    }

    override suspend fun send(
        amount: BigDecimal,
        address: String,
        memo: String?,
        feeRate: Int,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        transactionSorting: TransactionDataSortMode?,
        rbfEnabled: Boolean,
        changeToFirstInput: Boolean,
        utxoFilters: UtxoFilters
    ): String = executeSend(
        amount = amount,
        address = address,
        memo = memo,
        feeRate = feeRate,
        unspentOutputs = unspentOutputs,
        pluginData = pluginData,
        transactionSorting = transactionSorting,
        rbfEnabled = rbfEnabled,
        changeToFirstInput = changeToFirstInput,
        utxoFilters = utxoFilters
    ).header.uid

    override suspend fun sendForSwap(
        amount: BigDecimal,
        address: String,
        memo: String?,
        feeRate: Int,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        transactionSorting: TransactionDataSortMode?,
        rbfEnabled: Boolean,
        changeToFirstInput: Boolean,
        utxoFilters: UtxoFilters
    ): BitcoinSwapSendResult = executeSend(
        amount = amount,
        address = address,
        memo = memo,
        feeRate = feeRate,
        unspentOutputs = unspentOutputs,
        pluginData = pluginData,
        transactionSorting = transactionSorting,
        rbfEnabled = rbfEnabled,
        changeToFirstInput = changeToFirstInput,
        utxoFilters = utxoFilters
    ).let {
        BitcoinSwapSendResult(
            uid = it.header.uid,
            canonicalHashReversedHex = it.header.hash.toReversedHex()
        )
    }

    protected val isTrezorAccount: Boolean
        get() = wallet.account.type is AccountType.TrezorDevice

    /**
     * Trezor cannot build the time-locked P2SH script the Hodler plugin needs, so reject a
     * Hodler-carrying send on a Trezor account instead of signing a transaction it cannot honor.
     */
    private fun rejectHodlerForTrezor(pluginData: Map<Byte, IPluginData>?) {
        if (isTrezorAccount && pluginData?.containsKey(HodlerPlugin.id) == true) {
            throw LocalizedException(Res.string.send_trezor_time_lock_not_supported)
        }
    }

    private suspend fun executeSend(
        amount: BigDecimal,
        address: String,
        memo: String?,
        feeRate: Int,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        transactionSorting: TransactionDataSortMode?,
        rbfEnabled: Boolean,
        changeToFirstInput: Boolean,
        utxoFilters: UtxoFilters
    ): FullTransaction {
        rejectHodlerForTrezor(pluginData)
        val sortingType = getTransactionSortingType(transactionSorting)

        return kit.send(
            address = address,
            memo = memo,
            value = amount.movePointRight(decimal).toLong(),
            senderPay = true,
            feeRate = feeRate,
            sortType = sortingType,
            unspentOutputs = unspentOutputs,
            pluginData = pluginData ?: mapOf(),
            rbfEnabled = rbfEnabled,
            changeToFirstInput = changeToFirstInput,
            filters = utxoFilters,
        )
    }

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val result = kit.broadcastRawTransaction(rawTransactionHex)
        val status = when (result.status) {
            RawTransactionBroadcastStatus.Submitted -> BroadcastRawTransactionStatus.Submitted
            RawTransactionBroadcastStatus.Queued -> BroadcastRawTransactionStatus.Queued
            RawTransactionBroadcastStatus.AlreadyKnown -> BroadcastRawTransactionStatus.AlreadyKnown
        }
        return BroadcastRawTransactionResult(
            txHash = result.transaction.header.hash.toReversedHex(),
            status = status,
        )
    }

    override suspend fun signOffline(request: OfflineSignRequest): SignedOfflineBitcoinTransaction {
        val bitcoinRequest = requireNotNull(request as? OfflineBitcoinSignRequest) {
            "Unsupported offline sign request"
        }
        val transaction = kit.createSignedTransaction(
            address = bitcoinRequest.address,
            memo = bitcoinRequest.memo,
            value = bitcoinRequest.amount.movePointRight(decimal).toLong(),
            senderPay = true,
            feeRate = bitcoinRequest.feeRate,
            sortType = getTransactionSortingType(bitcoinRequest.transactionSorting),
            unspentOutputs = bitcoinRequest.unspentOutputs,
            pluginData = bitcoinRequest.pluginData ?: mapOf(),
            rbfEnabled = bitcoinRequest.rbfEnabled,
            changeToFirstInput = bitcoinRequest.changeToFirstInput,
            filters = bitcoinRequest.utxoFilters,
        )
        return SignedOfflineBitcoinTransaction(
            rawHex = serializeTransaction(transaction).toRawHexString(),
            txHash = transaction.header.hash.toReversedHex(),
            inputOutpoints = transaction.inputs.map { input ->
                OfflineTransactionOutpoint(
                    transactionHash = input.previousOutputTxHash.toReversedHex(),
                    outputIndex = input.previousOutputIndex,
                )
            }
        )
    }

    private fun serializeTransaction(transaction: FullTransaction): ByteArray {
        return kit.serializeTransaction(transaction)
    }

    private fun ByteArray.toReversedHex(): String =
        reversedArray().toRawHexString()

    override fun availableBalance(
        feeRate: Int,
        address: String?,
        memo: String?,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        changeToFirstInput: Boolean,
        utxoFilters: UtxoFilters
    ): BigDecimal {
        return try {
            val maximumSpendableValue = kit.maximumSpendableValue(
                address = address,
                memo = memo,
                feeRate = feeRate,
                unspentOutputInfos = unspentOutputs,
                pluginData = pluginData ?: mapOf(),
                changeToFirstInput = changeToFirstInput,
                filters = utxoFilters,
            )
            satoshiToBTC(maximumSpendableValue)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    override fun minimumSendAmount(address: String?): BigDecimal? {
        return try {
            satoshiToBTC(kit.minimumSpendableValue(address).toLong())
        } catch (e: Exception) {
            null
        }
    }

    override fun bitcoinFeeInfo(
        amount: BigDecimal,
        feeRate: Int,
        address: String?,
        memo: String?,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): BitcoinFeeInfo? {
        return try {
            val satoshiAmount = amount.movePointRight(decimal).toLong()
            kit.sendInfo(
                value = satoshiAmount,
                address = address,
                memo = memo,
                senderPay = true,
                feeRate = feeRate,
                unspentOutputs = unspentOutputs,
                pluginData = pluginData ?: mapOf(),
                changeToFirstInput = changeToFirstInput,
                filters = filters
            ).let {
                BitcoinFeeInfo(
                    unspentOutputs = it.unspentOutputs,
                    fee = satoshiToBTC(it.fee),
                    changeValue = it.changeValue?.let { satoshiToBTC(it) },
                    changeAddress = it.changeAddress
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun validate(address: String, pluginData: Map<Byte, IPluginData>?) {
        rejectHodlerForTrezor(pluginData)
        kit.validateAddress(address, pluginData ?: mapOf())
    }

    fun transactionRecord(transaction: TransactionInfo): BitcoinTransactionRecord {
        val from = transaction.inputs.find { input ->
            input.address?.isNotBlank() == true
        }?.address

        var transactionLockInfo: TransactionLockInfo? = null
        val lockedOutput = transaction.outputs.firstOrNull { it.pluginId == HodlerPlugin.id }
        if (lockedOutput != null) {
            val hodlerOutputData = lockedOutput.pluginData as? HodlerOutputData
            hodlerOutputData?.approxUnlockTime?.let { approxUnlockTime ->
                val lockedValueBTC = satoshiToBTC(lockedOutput.value)
                transactionLockInfo = TransactionLockInfo(
                    Date(approxUnlockTime * 1000),
                    hodlerOutputData.addressString,
                    lockedValueBTC,
                    hodlerOutputData.lockTimeInterval
                )
            }
        }
        val memo = transaction.outputs.firstOrNull { it.memo != null }?.memo

        val incoming = transaction.type == TransactionType.Incoming
        // Outgoing lists every recipient; the other types list only our own outputs.
        val outputs = transaction.outputs.filter { it.mine || transaction.type == TransactionType.Outgoing }
        val replacement = replacementRole(transaction)
        val amount = satoshiToBTC(transaction.amount)

        return BitcoinTransactionRecord(
            source = wallet.transactionSource,
            token = wallet.token,
            uid = transaction.uid,
            transactionHash = transaction.transactionHash,
            transactionIndex = transaction.transactionIndex,
            blockHeight = transaction.blockHeight,
            confirmationsThreshold = displayConfirmationsThreshold,
            timestamp = transaction.timestamp,
            fee = transaction.fee?.let { TransactionValue.CoinValue(wallet.token, satoshiToBTC(it)) },
            failed = transaction.status == TransactionStatus.INVALID,
            lockInfo = transactionLockInfo,
            conflictingHash = transaction.conflictingTxHash,
            showRawTransaction = transaction.status == TransactionStatus.NEW || transaction.status ==
                    TransactionStatus.INVALID,
            amount = if (incoming) amount else amount.negate(),
            from = from.takeIf { incoming },
            to = outputs.filter { !it.changeOutput }.mapNotNull { it.address }.distinct(),
            changeAddresses = outputs.filter { it.changeOutput }.mapNotNull { it.address }.distinct(),
            sentToSelf = transaction.type == TransactionType.SentToSelf,
            memo = memo,
            replaceable = !incoming && transaction.replaceable && replacement.replacedByHash == null,
            transactionRecordType = if (incoming) {
                TransactionRecordType.BITCOIN_INCOMING
            } else {
                TransactionRecordType.BITCOIN_OUTGOING
            },
            replacedByHash = replacement.replacedByHash,
            replacesHash = replacement.replacesHash,
            replacementChainUids = replacement.otherChainUids,
            ownReplacementConflict = replacement.ownConflict,
        )
    }

    private fun replacementRole(transaction: TransactionInfo): BitcoinReplacementRole {
        val chain = replacementRegistry.chainOf(
            wallet.account.id,
            wallet.token.blockchainType.uid,
            transaction.transactionHash
        ) ?: return replacedIncomingRole(transaction)
        return chain.roleOf(transaction.transactionHash, transaction.uid, transaction.conflictingTxHash) { hash ->
            val member = if (hash == transaction.transactionHash) transaction else kit.getTransaction(hash)
            member.replacementState()
        }
    }

    /** Only the kit's conflict reveals a sender's speed-up here; anything but a full direct replacement stays visible. */
    private fun replacedIncomingRole(transaction: TransactionInfo): BitcoinReplacementRole {
        val winner = transaction.conflictingTxHash
            ?.takeIf { transaction.type == TransactionType.Incoming }
            ?.let(kit::getTransaction)
            ?: return BitcoinReplacementRole()
        val paidInFull = winner.type == TransactionType.Incoming &&
            winner.conflictingTxHash == null &&
            winner.amount >= transaction.amount &&
            winner.spendsSameOutputAs(transaction)
        return if (paidInFull) {
            BitcoinReplacementRole(replacedByHash = winner.transactionHash, ownConflict = true)
        } else {
            BitcoinReplacementRole()
        }
    }

    private fun TransactionInfo.spendsSameOutputAs(other: TransactionInfo): Boolean {
        // The input table's key omits the transaction hash: a replacement with the same sequence overwrites the input.
        if (other.inputs.isEmpty()) return other.status != TransactionStatus.INVALID
        val spent = inputs.mapNotNullTo(mutableSetOf()) { it.spentOutput }
        return other.inputs.any { it.spentOutput in spent }
    }

    // The kit looks a hash up only among valid transactions, so an invalidated one reads as absent.
    private fun TransactionInfo?.replacementState() = when {
        this == null || status == TransactionStatus.INVALID -> ReplacementMemberState.Dead
        blockHeight != null -> ReplacementMemberState.Mined
        else -> ReplacementMemberState.Alive
    }

    override val statusInfo: Map<String, Any>
        get() = networkErrorTracker.appendNetworkErrors(
            kit.statusInfo(), wallet.token.blockchainType, wallet.account.id
        )

    override fun satoshiToBTC(value: Long): BigDecimal {
        return BigDecimal(value).movePointLeft(decimal)
    }

    override fun isTransactionInSendQueue(txHash: String): Boolean {
        val hashBytes = tryOrNull { txHash.hexToByteArray() } ?: return false
        return kit.getTransactionsInSendQueue().any { it.contentEquals(hashBytes) }
    }

    companion object {
        fun getTransactionSortingType(sortType: TransactionDataSortMode?): TransactionDataSortType =
            when (sortType) {
                TransactionDataSortMode.Bip69 -> TransactionDataSortType.Bip69
                else -> TransactionDataSortType.Shuffle
            }

        // Resolved per kit, like the hardware keys it reads.
        @JvmStatic
        protected val hardwareSigners: BitcoinHardwareSigners
            get() = get(BitcoinHardwareSigners::class.java)
    }

}
