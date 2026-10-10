package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.adapters.SolanaTransactionConverterTestFixture.USER_ADDRESS
import cash.p.terminal.core.adapters.SolanaTransactionConverterTestFixture.usdcToken
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SolanaTransactionConverterSignTest {

    private val coinManager: ICoinManager = mockk(relaxed = true) {
        every { getToken(any()) } returns usdcToken
    }

    private fun mainValueOf(incoming: Boolean, amount: String): BigDecimal {
        val fullTransaction = SolanaTransactionConverterTestFixture.tokenTransferTransaction(
            hash = "hash",
            from = if (incoming) "SENDER_ADDRESS" else USER_ADDRESS,
            to = if (incoming) USER_ADDRESS else "RECIPIENT_ACCOUNT",
            mint = "USDC_MINT",
            incoming = incoming,
            amount = BigDecimal(amount)
        )
        val record = SolanaTransactionConverterTestFixture.createConverter(coinManager)
            .transactionRecord(fullTransaction)
        assertEquals(
            if (incoming) TransactionRecordType.SOLANA_INCOMING else TransactionRecordType.SOLANA_OUTGOING,
            record.transactionRecordType
        )
        return (record.mainValue as TransactionValue.CoinValue).value
    }

    @Test
    fun transactionRecord_syncedOutgoingSplWithPositiveAmount_mainValueIsNegative() {
        assertEquals(0, BigDecimal("-1").compareTo(mainValueOf(incoming = false, amount = "1000000")))
    }

    @Test
    fun transactionRecord_localOutgoingSplWithNegativeAmount_mainValueStaysNegative() {
        assertEquals(0, BigDecimal("-1").compareTo(mainValueOf(incoming = false, amount = "-1000000")))
    }

    @Test
    fun transactionRecord_incomingSpl_mainValueIsPositive() {
        assertEquals(0, BigDecimal("1").compareTo(mainValueOf(incoming = true, amount = "1000000")))
    }
}
