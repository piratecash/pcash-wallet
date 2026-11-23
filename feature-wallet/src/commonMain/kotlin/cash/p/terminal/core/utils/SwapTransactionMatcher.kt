package cash.p.terminal.core.utils

import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

/**
 * Represents an incoming transaction to be matched with a swap.
 */
data class IncomingTransaction(
    val uid: String,
    val amount: BigDecimal?,
    val timestamp: Long,  // in milliseconds
    val token: Token,
    val addresses: List<String>?,
)

/** [linkAmount] is set when the match is not stored yet and [SwapTransactionMatcher.findMatchingSwap] records it. */
data class IncomingSwapMatch(val swap: SwapProviderTransaction, val linkAmount: BigDecimal?)

/**
 * Matches incoming transactions to swap operations.
 * Handles address-based matching, amount+timestamp matching, and fallback timestamp matching.
 */
class SwapTransactionMatcher(
    private val storage: SwapProviderTransactionsStorage,
    private val accountManager: IAccountManager,
) {

    companion object {
        const val TIME_WINDOW_MS = 10_800_000L // 3 hours
        const val AMOUNT_TOLERANCE = 0.005 // 0.5%
    }

    /**
     * Finds a matching swap for an incoming transaction. Read-only: [findMatchingSwap] records the match.
     *
     * Matching priority:
     * 1. Already matched (cached by incomingRecordUid)
     * 2. Address + amount matching (when addresses available)
     * 3. Amount + timestamp matching (when addresses not available)
     * 4. Fallback to timestamp-only matching
     *
     * @param transaction The incoming transaction to match
     * @return The match or null if no match found
     */
    fun findMatch(transaction: IncomingTransaction): IncomingSwapMatch? {
        // Captured before any DB call: an account switch mid-lookup must not match against the new account.
        val accountId = accountManager.activeAccount?.id.orEmpty()

        // 1. Check if already matched
        storage.getByIncomingRecordUid(transaction.uid)?.let { return IncomingSwapMatch(it, linkAmount = null) }

        findByAmount(transaction, accountId)?.let { return IncomingSwapMatch(it, linkAmount = transaction.amount) }

        // 4. Fallback to timestamp-only matching
        return storage.getByTokenOut(
            token = transaction.token,
            timestamp = transaction.timestamp,
            accountId = accountId
        )?.let { IncomingSwapMatch(it, linkAmount = null) }
    }

    private fun findByAmount(transaction: IncomingTransaction, accountId: String): SwapProviderTransaction? {
        val amount = transaction.amount ?: return null
        val addresses = transaction.addresses

        // 2. Try address + amount matching
        if (!addresses.isNullOrEmpty()) {
            return addresses.firstNotNullOfOrNull { address ->
                storage.getByAddressAndAmount(
                    address = address,
                    token = transaction.token,
                    accountId = accountId,
                    amount = amount,
                    timestamp = transaction.timestamp
                )
            }
        }

        // 3. Amount + timestamp matching (no addresses)
        // Query returns results ordered by closest amount match, then by date
        return storage.getUnmatchedSwapsByTokenOut(
            token = transaction.token,
            fromTimestamp = transaction.timestamp - TIME_WINDOW_MS,
            toTimestamp = transaction.timestamp,
            amount = amount,
            tolerance = AMOUNT_TOLERANCE,
            accountId = accountId
        ).firstOrNull()
    }

    /** [findMatch] that also stores an address or amount match as the swap's incoming record. */
    fun findMatchingSwap(transaction: IncomingTransaction): SwapProviderTransaction? {
        val match = findMatch(transaction) ?: return null
        match.linkAmount?.let { amount ->
            storage.setIncomingRecordUid(
                date = match.swap.date,
                incomingRecordUid = transaction.uid,
                amountOutReal = amount
            )
        }
        return match.swap
    }

}
