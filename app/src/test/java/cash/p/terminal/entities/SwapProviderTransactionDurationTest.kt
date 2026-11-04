package cash.p.terminal.entities

import cash.p.terminal.network.swaprepository.SwapProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SwapProviderTransactionDurationTest {

    @Test
    fun swapDuration_receivedAtAfterDate_exactFromReceivedAt() {
        assertEquals(
            SwapDuration(500L, approximate = false),
            tx(receivedAt = 1_500L, finishedAt = 9_000L).swapDuration()
        )
    }

    @Test
    fun swapDuration_onlyFinishedAt_approximateFromFinishedAt() {
        assertEquals(SwapDuration(2_000L, approximate = true), tx(finishedAt = 3_000L).swapDuration())
    }

    @Test
    fun swapDuration_receivedAtNotAfterDate_approximateFromFinishedAt() {
        assertEquals(
            SwapDuration(2_000L, approximate = true),
            tx(receivedAt = 1_000L, finishedAt = 3_000L).swapDuration()
        )
    }

    @Test
    fun swapDuration_thorchainOnlyFinishedAt_returnsNull() {
        assertNull(tx(provider = SwapProvider.THORCHAIN, finishedAt = 3_000L).swapDuration())
    }

    @Test
    fun swapDuration_mayaOnlyFinishedAt_returnsNull() {
        assertNull(tx(provider = SwapProvider.MAYA, finishedAt = 3_000L).swapDuration())
    }

    @Test
    fun swapDuration_thorchainWithReceivedAt_exactFromReceivedAt() {
        val tx = tx(provider = SwapProvider.THORCHAIN, receivedAt = 1_800L, finishedAt = 3_000L)

        assertEquals(SwapDuration(800L, approximate = false), tx.swapDuration())
    }

    @Test
    fun swapDuration_notFinishedStatus_returnsNull() {
        listOf("refunded", "failed", "exchanging").forEach { status ->
            assertNull(status, tx(status = status, receivedAt = 1_500L, finishedAt = 3_000L).swapDuration())
        }
    }

    @Test
    fun swapDuration_noCompletionTime_returnsNull() {
        assertNull(tx().swapDuration())
    }

    @Test
    fun swapDuration_completionTimesNotAfterDate_returnsNull() {
        assertNull(tx(receivedAt = 1_000L, finishedAt = 500L).swapDuration())
    }

    private fun tx(
        status: String = "finished",
        provider: SwapProvider = SwapProvider.CHANGENOW,
        receivedAt: Long? = null,
        finishedAt: Long? = null,
    ) = SwapProviderTransaction(
        date = 1_000L,
        outgoingRecordUid = null,
        transactionId = "id",
        status = status,
        provider = provider,
        coinUidIn = "in",
        blockchainTypeIn = "bitcoin",
        amountIn = BigDecimal.ONE,
        addressIn = "a",
        coinUidOut = "out",
        blockchainTypeOut = "ethereum",
        amountOut = BigDecimal.ONE,
        addressOut = "b",
        finishedAt = finishedAt,
        receivedAt = receivedAt,
    )
}
