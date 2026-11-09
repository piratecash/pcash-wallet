package cash.p.terminal.core.usecase

import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class RelinkSwapToReplacementUseCase(
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val pendingMultiSwapStorage: PendingMultiSwapStorage,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     * Moves the swap held by any of [candidateUids] onto [toUid]. Deposit hashes are compared with every
     * [chainHashes] member, so the order in which members were relinked does not matter.
     * Returns the swap as stored afterwards, or null when no candidate holds one.
     */
    suspend fun relink(
        candidateUids: Collection<String>,
        chainHashes: Collection<String>,
        toUid: String,
        toHash: String,
    ): SwapProviderTransaction? = withContext(dispatcherProvider.io) {
        val fromUids = candidateUids.filter { it != toUid }
        if (fromUids.isNotEmpty()) pendingMultiSwapStorage.moveLeg1RecordUid(fromUids, toUid)

        val swap = candidateUids.firstNotNullOfOrNull(swapProviderTransactionsStorage::getByOutgoingRecordUid)
            ?: return@withContext null
        val canonicalChainHashes = chainHashes.mapTo(HashSet()) { it.canonicalTransactionHash() }
        fun String.relinked() = if (canonicalTransactionHash() in canonicalChainHashes) toHash else this
        val relinked = swap.copy(
            outgoingRecordUid = toUid,
            transactionId = swap.transactionId.relinked(),
            depositTransactionHash = swap.depositTransactionHash?.relinked(),
        )
        if (relinked != swap) {
            swapProviderTransactionsStorage.relinkOutgoingRecord(
                date = relinked.date,
                outgoingRecordUid = toUid,
                transactionId = relinked.transactionId,
                depositTransactionHash = relinked.depositTransactionHash,
            )
        }
        relinked
    }
}
