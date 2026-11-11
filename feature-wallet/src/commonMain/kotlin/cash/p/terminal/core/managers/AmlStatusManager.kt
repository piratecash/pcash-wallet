package cash.p.terminal.core.managers

import cash.p.terminal.entities.Address
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.modules.transactions.CheckAmlIncomingTransactionUseCase
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.getSenderAddresses
import cash.p.terminal.modules.transactions.isIncomingForAmlCheck
import cash.p.terminal.premium.domain.PremiumSettings
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import co.touchlab.kermit.Logger
import java.util.concurrent.ConcurrentHashMap

private val logger = Logger.withTag("AmlStatusManager")

class AmlStatusManager(
    private val checkAmlIncomingTransaction: CheckAmlIncomingTransactionUseCase,
    private val premiumSettings: PremiumSettings,
    private val dispatcherProvider: DispatcherProvider
) {
    // Manager-owned scope for processing requests (independent of UI lifecycle)
    private val managerScope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    private val amlStatuses = ConcurrentHashMap<String, AmlStatus>()
    private val addressStatuses = ConcurrentHashMap<String, AmlStatus>()

    // Queue for pending requests (LIFO priority - most recent at end)
    private val pendingQueue = ArrayDeque<PendingRequest>(MAX_QUEUE_SIZE + 1)
    private val pendingUids = mutableSetOf<String>() // For O(1) lookup
    private val activeRequests = ConcurrentHashMap<String, Job>()
    private val queueMutex = Mutex()

    // Flag to prevent processing after clearAll
    @Volatile
    private var isCleared = false

    // Moved by a reload: work begun before it belongs to the previous level and must not run after it.
    @Volatile
    private var generation = 0

    private val _statusUpdates = MutableSharedFlow<AmlStatusUpdate>()
    val statusUpdates = _statusUpdates.asSharedFlow()

    private val _enabledStateFlow = MutableStateFlow(premiumSettings.getAmlCheckReceivedEnabled())
    val enabledStateFlow = _enabledStateFlow.asStateFlow()

    val isEnabled: Boolean
        get() = _enabledStateFlow.value

    fun setEnabled(enabled: Boolean) {
        premiumSettings.setAmlCheckReceivedEnabled(enabled)
        _enabledStateFlow.value = enabled
        if (enabled) {
            isCleared = false
        } else {
            clearAll()
        }
    }

    /** The flag belongs to the user level: a desktop level change moves it without [setEnabled]. */
    fun reloadEnabled() {
        val enabled = premiumSettings.getAmlCheckReceivedEnabled()
        generation++
        clearAll()
        isCleared = !enabled
        _enabledStateFlow.value = enabled
    }

    fun getStatus(uid: String): AmlStatus? = amlStatuses[uid]

    fun getAddressStatus(address: String): AmlStatus? = addressStatuses[address]

    fun fetchStatusIfNeeded(uid: String, record: TransactionRecord) {
        if (!isEnabled) return
        if (isCleared) return
        if (!record.isIncomingForAmlCheck()) return
        if (record.getSenderAddresses().isEmpty()) return

        val requestGeneration = generation
        managerScope.launch {
            queueMutex.withLock {
                // Re-check inside lock to prevent race conditions
                if (isCleared) return@withLock
                if (amlStatuses.containsKey(uid)) return@withLock
                if (activeRequests.containsKey(uid)) return@withLock

                // Remove if already in queue (to re-add with higher priority)
                if (pendingUids.contains(uid)) {
                    pendingQueue.removeAll { it.uid == uid }
                    pendingUids.remove(uid)
                }

                // Add to end (highest priority position)
                val request = PendingRequest(uid, record, requestGeneration)
                pendingQueue.addLast(request)
                pendingUids.add(uid)

                // Enforce max queue size - remove oldest (first) items
                while (pendingQueue.size > MAX_QUEUE_SIZE) {
                    val removed = pendingQueue.removeFirst()
                    pendingUids.remove(removed.uid)
                }
            }
            processQueue()
        }
    }

    private suspend fun processQueue() {
        // Early exit if cleared
        if (isCleared) return

        queueMutex.withLock {
            // Re-check inside lock
            if (isCleared) return

            while (activeRequests.size < MAX_CONCURRENT_REQUESTS && pendingQueue.isNotEmpty()) {
                // Take last item (most recently added = highest priority)
                val request = pendingQueue.removeLast()
                pendingUids.remove(request.uid)
                if (request.generation == generation) launchRequest(request)
            }
        }
    }

    private fun launchRequest(request: PendingRequest) {
        val (uid, record, requestGeneration) = request
        val token = record.token

        amlStatuses[uid] = AmlStatus.Loading
        emitUpdate(uid, AmlStatus.Loading)

        // Registered before it starts: a reload either cancels it or is seen by the check below.
        val job = managerScope.launch(dispatcherProvider.io, CoroutineStart.LAZY) {
            try {
                if (requestGeneration != generation) {
                    amlStatuses.remove(uid)
                    return@launch
                }
                val addresses = record.getSenderAddresses().map { Address(it) }
                val results = checkAmlIncomingTransaction(addresses, token)

                // Store per-address statuses
                results.perAddress.forEach { (address, result) ->
                    addressStatuses[address] = AmlStatus.from(result)
                }

                // Store overall transaction status
                val status = AmlStatus.from(results.overall)
                amlStatuses[uid] = status
                evictCacheIfNeeded()
                emitUpdate(uid, status)
            } catch (e: Throwable) {
                logger.w(e) { "AmlStatusManager: failed to check AML status for tx $uid" }
                amlStatuses.remove(uid)
                emitUpdate(uid, null)
            } finally {
                activeRequests.remove(uid)
                processQueue()
            }
        }

        activeRequests[uid] = job
        job.start()
    }

    fun clearAll() {
        isCleared = true

        // Cancel all active requests
        activeRequests.values.forEach { it.cancel() }
        activeRequests.clear()
        amlStatuses.clear()
        addressStatuses.clear()

        // Clear queue synchronously using tryLock to avoid blocking
        if (queueMutex.tryLock()) {
            try {
                pendingQueue.clear()
                pendingUids.clear()
            } finally {
                queueMutex.unlock()
            }
        }
        // If lock is held, the coroutine holding it will see isCleared flag
        // and won't process further. Flag is reset in setEnabled(true).
    }

    private fun evictCacheIfNeeded() {
        if (amlStatuses.size > MAX_CACHE_SIZE) {
            val toRemove = amlStatuses.keys.take(MAX_CACHE_SIZE / 5)
            toRemove.forEach { amlStatuses.remove(it) }
        }
        if (addressStatuses.size > MAX_ADDRESS_CACHE_SIZE) {
            val toRemove = addressStatuses.keys.take(MAX_ADDRESS_CACHE_SIZE / 5)
            toRemove.forEach { addressStatuses.remove(it) }
        }
    }

    private fun emitUpdate(uid: String, status: AmlStatus?) {
        managerScope.launch {
            _statusUpdates.emit(AmlStatusUpdate(uid, status))
        }
    }

    private data class PendingRequest(
        val uid: String,
        val record: TransactionRecord,
        val generation: Int
    )

    data class AmlStatusUpdate(
        val uid: String,
        val status: AmlStatus?
    )

    fun applyStatus(viewItem: TransactionViewItem): TransactionViewItem {
        val status = amlStatuses[viewItem.uid] ?: return viewItem
        return viewItem.copy(amlStatus = status)
    }

    companion object {
        private const val MAX_QUEUE_SIZE = 20
        private const val MAX_CONCURRENT_REQUESTS = 10
        private const val MAX_CACHE_SIZE = 500
        private const val MAX_ADDRESS_CACHE_SIZE = 1000
    }
}
