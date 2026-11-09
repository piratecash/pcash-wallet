package cash.p.terminal.core.usecase

import cash.p.terminal.core.adapters.BitcoinReplacementLink
import cash.p.terminal.core.adapters.BitcoinReplacementRegistry
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import co.touchlab.kermit.Logger
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class RecordBitcoinReplacementUseCase(
    private val registry: BitcoinReplacementRegistry,
    private val relinkSwapToReplacement: RelinkSwapToReplacementUseCase,
    private val locallyCreatedTransactions: LocallyCreatedTransactionRepository,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logger = Logger.withTag("RecordBitcoinReplacement")

    /** Never throws and survives the screen closing: the replacement is already broadcast. */
    suspend operator fun invoke(
        accountId: String,
        blockchainTypeUid: String,
        original: BitcoinTransactionRecord,
        replacement: FullTransaction,
    ) {
        withContext(dispatcherProvider.io + NonCancellable) {
            try {
                record(accountId, blockchainTypeUid, original, replacement)
            } catch (e: Exception) {
                logger.w(e) { "Failed to record the replacement" }
            }
        }
    }

    private suspend fun record(
        accountId: String,
        blockchainTypeUid: String,
        original: BitcoinTransactionRecord,
        replacement: FullTransaction,
    ) {
        val replacementHash = replacement.header.hash.toReversedHex()
        val replacementUid = replacement.header.uid
        locallyCreatedTransactions.markCreated(accountId, blockchainTypeUid, replacementHash)
        registry.remember(
            accountId,
            blockchainTypeUid,
            BitcoinReplacementLink(original.transactionHash, original.uid, replacementHash, replacementUid),
        )
        val chainHashes = registry.chainOf(accountId, blockchainTypeUid, replacementHash)?.hashes.orEmpty()
        relinkSwapToReplacement.relink(
            candidateUids = listOf(original.uid, replacementUid),
            chainHashes = chainHashes,
            toUid = replacementUid,
            toHash = replacementHash,
        )
    }
}
