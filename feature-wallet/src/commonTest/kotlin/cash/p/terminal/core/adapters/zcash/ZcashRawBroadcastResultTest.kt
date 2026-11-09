package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.zcash.BroadcastResult
import cash.p.zcash.ZcashException
import cash.p.terminal.core.managers.NotBroadcastException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

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

    @Test
    fun toBroadcastResult_zebraDoubleSend_returnsAlreadyKnownWithRequestedTxid() {
        val result = BroadcastResult(errorCode = -1, message = "transaction already exists in mempool")
            .toBroadcastResult(txHash = TX_HASH)

        assertEquals(TX_HASH, result.txHash)
        assertEquals(BroadcastRawTransactionStatus.AlreadyKnown, result.status)
    }

    @Test
    fun toBroadcastResult_rejectionReleasedTheInputs_isMarkedNotBroadcast() {
        val failure = assertFailsWith<NotBroadcastException> {
            BroadcastResult(errorCode = -25, message = "refused", inputsReleased = true)
                .toBroadcastResult(txHash = TX_HASH)
        }

        assertTrue(failure.cause is ZcashException)
        assertTrue(failure.cause.message.orEmpty().contains("refused"))
    }

    private companion object {
        val TX_HASH = (0 until 32).joinToString(separator = "") { "%02x".format(it) }
    }
}
