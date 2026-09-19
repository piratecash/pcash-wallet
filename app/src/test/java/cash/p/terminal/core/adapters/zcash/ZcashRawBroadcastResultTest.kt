package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.zcash.BroadcastResult
import cash.p.zcash.ZcashException
import org.junit.Assert.assertEquals
import org.junit.Test

class ZcashRawBroadcastResultTest {

    @Test
    fun toBroadcastResult_accepted_returnsSubmittedWithAssignedTxid() {
        val result = BroadcastResult(errorCode = 0, message = TX_HASH.uppercase())
            .toBroadcastResult(txHash = "unused")

        assertEquals(TX_HASH, result.txHash)
        assertEquals(BroadcastRawTransactionStatus.Submitted, result.status)
    }

    @Test
    fun toBroadcastResult_alreadyCommittedRejection_returnsAlreadyKnownWithRequestedTxid() {
        val result = BroadcastResult(
            errorCode = -1,
            message = "any transaction with the same effects will be rejected from the mempool " +
                "until a chain reset: transaction was committed to the best chain"
        ).toBroadcastResult(txHash = "0x$TX_HASH")

        assertEquals(TX_HASH, result.txHash)
        assertEquals(BroadcastRawTransactionStatus.AlreadyKnown, result.status)
    }

    @Test(expected = ZcashException::class)
    fun toBroadcastResult_otherRejection_throws() {
        BroadcastResult(errorCode = -1, message = "insufficient funds")
            .toBroadcastResult(txHash = TX_HASH)
    }

    private companion object {
        val TX_HASH = (0 until 32).joinToString(separator = "") { "%02x".format(it) }
    }
}
