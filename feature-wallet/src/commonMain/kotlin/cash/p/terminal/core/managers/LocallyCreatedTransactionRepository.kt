package cash.p.terminal.core.managers

import co.touchlab.kermit.Logger
import cash.p.terminal.core.storage.LocallyCreatedTransactionStorage
import cash.p.terminal.entities.LocallyCreatedTransactionRecord
import cash.p.terminal.entities.PendingTransactionEntity
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.hours

private val logger = Logger.withTag("LocallyCreatedTransactionRepository")

class LocallyCreatedTransactionRepository(
    private val storage: LocallyCreatedTransactionStorage,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val mutex = Mutex()
    private val readFailureLogged = AtomicBoolean(false)
    private val _changedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changedFlow: SharedFlow<Unit> = _changedFlow.asSharedFlow()

    suspend fun isCreated(record: TransactionRecord): Boolean {
        val accountId = record.source.account.id
        val blockchainTypeUid = record.blockchainType.uid
        return isCreated(accountId, blockchainTypeUid, record.transactionHash) ||
            isNftTransferCreated(record, accountId, blockchainTypeUid)
    }

    suspend fun isCreated(
        accountId: String,
        blockchainTypeUid: String,
        transactionHash: String?,
    ): Boolean {
        val normalizedHash = normalizeHash(transactionHash) ?: return false
        return readOrFalse { storage.exists(accountId, blockchainTypeUid, normalizedHash) }
    }

    /** The blockchain record's hash is unknown at send time, so the send is keyed by NFT, recipient and time. */
    suspend fun markNftTransferCreated(accountId: String, nftAddress: String, recipient: String) {
        val prefix = nftTransferKeyPrefix(nftAddress, recipient) ?: return
        val now = System.currentTimeMillis()
        markCreated(accountId, BlockchainType.Ton.uid, "$prefix$now", createdAt = now)
    }

    private suspend fun isNftTransferCreated(
        record: TransactionRecord,
        accountId: String,
        blockchainTypeUid: String,
    ): Boolean {
        val prefixes = (record as? TonTransactionRecord)?.nftTransferKeyPrefixes().orEmpty()
        if (prefixes.isEmpty()) return false
        val recordMs = record.timestamp * 1000
        return readOrFalse {
            storage.hashesCreatedBetween(
                accountId = accountId,
                blockchainTypeUid = blockchainTypeUid,
                fromMs = recordMs - NFT_TRANSFER_MARK_WINDOW_MS,
                toMs = recordMs + NFT_TRANSFER_MARK_WINDOW_MS,
            ).any { hash -> prefixes.any(hash::startsWith) }
        }
    }

    private fun TonTransactionRecord.nftTransferKeyPrefixes(): List<String> = actions.mapNotNull { action ->
        val send = action.type as? TonTransactionRecord.Action.Type.Send ?: return@mapNotNull null
        val nft = send.value as? TransactionValue.NftValue ?: return@mapNotNull null
        nftTransferKeyPrefix(nft.mintAddress, send.to)
    }

    private fun nftTransferKeyPrefix(nftAddress: String, recipient: String): String? {
        val nft = tonRawAddressOrNull(nftAddress) ?: return null
        val to = tonRawAddressOrNull(recipient) ?: return null
        return "$NFT_TRANSFER_KEY:$nft:$to:"
    }

    private suspend fun readOrFalse(read: suspend () -> Boolean): Boolean = withContext(dispatcherProvider.io) {
        try {
            read()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logReadFailureOnce(error)
            false
        }
    }

    suspend fun markCreated(wallet: Wallet, transactionHash: String?) {
        markCreated(
            accountId = wallet.account.id,
            blockchainTypeUid = wallet.token.blockchainType.uid,
            transactionHash = transactionHash,
        )
    }

    suspend fun markCreated(entity: PendingTransactionEntity, transactionHash: String?) {
        markCreated(
            accountId = entity.walletId,
            blockchainTypeUid = entity.blockchainTypeUid,
            transactionHash = transactionHash,
            createdAt = entity.createdAt,
        )
    }

    suspend fun markCreated(record: TransactionRecord) {
        markCreated(
            accountId = record.source.account.id,
            blockchainTypeUid = record.blockchainType.uid,
            transactionHash = record.transactionHash,
            createdAt = record.timestamp * 1000,
        )
    }

    suspend fun markCreated(
        accountId: String,
        blockchainTypeUid: String,
        transactionHash: String?,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        val normalizedHash = normalizeHash(transactionHash) ?: return

        runBestEffort("mark locally created transaction") {
            withContext(NonCancellable + dispatcherProvider.io) {
                mutex.withLock {
                    val inserted = storage.insert(
                        LocallyCreatedTransactionRecord(
                            accountId = accountId,
                            blockchainTypeUid = blockchainTypeUid,
                            transactionHash = normalizedHash,
                            createdAt = createdAt,
                        )
                    )
                    if (inserted) {
                        trimAccountIfNeeded(accountId)
                        _changedFlow.tryEmit(Unit)
                    }
                }
            }
        }
    }

    suspend fun trimAllAccounts() = runBestEffort("trim locally created transactions") {
        mutex.withLock {
            withContext(dispatcherProvider.io) {
                storage.trimAllAccounts(MAX_RECORDS_PER_ACCOUNT)
            }
        }
    }

    suspend fun deleteByAccountIds(accountIds: List<String>) =
        runBestEffort("delete locally created transactions") {
            mutex.withLock {
                withContext(dispatcherProvider.io) {
                    storage.deleteByAccountIds(accountIds)
                }
                _changedFlow.tryEmit(Unit)
            }
        }

    suspend fun count(accountId: String): Int = withContext(dispatcherProvider.io) {
        storage.count(accountId)
    }

    private fun normalizeHash(transactionHash: String?): String? {
        return transactionHash?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun trimAccountIfNeeded(accountId: String) {
        if (storage.count(accountId) > MAX_RECORDS_PER_ACCOUNT) {
            storage.trimAccount(accountId, MAX_RECORDS_PER_ACCOUNT)
        }
    }

    private suspend fun runBestEffort(action: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.e(error) { "Failed to $action" }
        }
    }

    private fun logReadFailureOnce(error: Exception) {
        if (readFailureLogged.compareAndSet(false, true)) {
            logger.e(error) { "Failed to read locally created transaction" }
        }
    }

    companion object {
        const val MAX_RECORDS_PER_ACCOUNT = 20_000
        private const val NFT_TRANSFER_KEY = "nft-transfer"
        private val NFT_TRANSFER_MARK_WINDOW_MS = 1.hours.inWholeMilliseconds
    }
}
