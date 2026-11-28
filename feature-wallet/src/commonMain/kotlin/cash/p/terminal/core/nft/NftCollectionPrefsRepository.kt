package cash.p.terminal.core.nft

import cash.p.terminal.core.storage.NftCollectionPrefStorage
import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftUserTrust
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val logger = Logger.withTag("NftCollectionPrefsRepository")

/**
 * Writes go through one FIFO queue drained in the application scope: they outlive the screen that
 * made them (Undo after the preview closed) and apply strictly in call order.
 */
class NftCollectionPrefsRepository(
    private val storage: NftCollectionPrefStorage,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val operations = Channel<PrefOperation>(Channel.UNLIMITED)

    init {
        dispatcherProvider.applicationScope.launch(dispatcherProvider.io) {
            for (operation in operations) operation.run()
        }
    }

    fun prefsFlow(accountId: String): Flow<Map<NftCollectionPrefKey, NftCollectionPrefRecord>> =
        storage.observe(accountId)
            .map { records -> records.associateBy { it.key } }
            .flowOn(dispatcherProvider.io)

    fun setHidden(item: NftItemRecord, hidden: Boolean) = update(item) { it.copy(hidden = hidden) }

    fun markSpam(item: NftItemRecord) = update(item) { it.copy(userTrust = NftUserTrust.Spam) }

    fun markNotSpam(item: NftItemRecord) = update(item) { it.copy(userTrust = NftUserTrust.Trust, hidden = false) }

    /** Queued behind earlier writes, so none of them can bring the rows back afterwards. */
    suspend fun deleteByAccountIds(accountIds: List<String>) {
        enqueue { storage.deleteByAccountIds(accountIds) }.await()
    }

    private fun update(item: NftItemRecord, change: (NftCollectionPrefRecord) -> NftCollectionPrefRecord) {
        enqueue {
            val current = storage.get(item.accountId, item.blockchainType, item.collectionKey)
                ?: NftCollectionPrefRecord(
                    accountId = item.accountId,
                    blockchainType = item.blockchainType,
                    collectionKey = item.collectionKey,
                    hidden = false,
                    userTrust = NftUserTrust.None,
                )
            storage.upsert(change(current))
        }
    }

    private fun enqueue(action: suspend () -> Unit): CompletableDeferred<Unit> =
        PrefOperation(action).also { operations.trySend(it) }.done

    private class PrefOperation(private val action: suspend () -> Unit) {
        val done = CompletableDeferred<Unit>()

        suspend fun run() {
            try {
                action()
                done.complete(Unit)
            } catch (e: CancellationException) {
                done.cancel(e)
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Failed to apply an NFT collection preference change" }
                done.completeExceptionally(e)
            }
        }
    }
}
